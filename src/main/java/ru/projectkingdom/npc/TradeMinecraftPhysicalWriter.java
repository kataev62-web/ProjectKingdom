package ru.projectkingdom.npc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server-thread-only, loaded-chunk physical adapter. NOT a durable two-container transaction.
 * Caller must block other inventory mutations while a projection is in progress.
 */
public final class TradeMinecraftPhysicalWriter implements TradePhysicalProjectionCoordinator.PhysicalWriter {
    /** Balance must be read/written from the same physical cash authority used by the game. */
    public interface CashAccess {
        long readCopper(UUID enterprise) throws IOException;
        void writeCopper(UUID enterprise, long value) throws IOException;
    }
    private final ServerLevel level;
    private final CashAccess cash;
    private final Map<UUID, BlockPos> locations;
    public TradeMinecraftPhysicalWriter(ServerLevel level, CashAccess cash, Map<UUID, BlockPos> locations) {
        this.level=Objects.requireNonNull(level);
        this.cash=Objects.requireNonNull(cash);
        this.locations=Map.copyOf(locations);
    }
    private void serverThread() throws IOException {
        if(!level.getServer().isSameThread()) throw new IOException("physical trade must run on server thread");
    }
    private Container loaded(UUID id) throws IOException {
        BlockPos pos=locations.get(id);
        if(pos==null || !level.hasChunkAt(pos)) return null; // Never force-load a chunk.
        BlockEntity entity=level.getBlockEntity(pos);
        return entity instanceof Container c ? c : null;
    }
    @Override public TradeAtomicEconomy.Account read(UUID id) throws IOException {
        serverThread();
        Container c=loaded(id);
        return c==null?null:TradeMinecraftContainerView.snapshot(id,cash.readCopper(id),c);
    }
    @Override public boolean compareAndSet(UUID id, TradeAtomicEconomy.Account expected,
                                             TradeAtomicEconomy.Account replacement) throws IOException {
        serverThread();
        if(!id.equals(expected.id()) || !id.equals(replacement.id())) return false;
        Container c=loaded(id);
        if(c==null)return false;
        TradeAtomicEconomy.Account actual=TradeMinecraftContainerView.snapshot(id,cash.readCopper(id),c);
        if(!equal(expected,actual) || c.getContainerSize()!=replacement.slots().size())return false;
        // Validate and construct ALL stacks before mutating any slot.
        ItemStack[] newStacks=new ItemStack[replacement.slots().size()];
        for(int i=0;i<newStacks.length;i++){
            var s=replacement.slots().get(i);
            if(s==null){newStacks[i]=ItemStack.EMPTY;continue;}
            ResourceLocation key=ResourceLocation.tryParse(s.item());
            if(key==null || !BuiltInRegistries.ITEM.containsKey(key))return false;
            Item item=BuiltInRegistries.ITEM.get(key);
            ItemStack stack=new ItemStack(item,s.count());
            CompoundTag tag;
            try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(s.tag()))){
                tag=NbtIo.read(input);
                if(input.available()!=0)return false;
            }catch(Exception badTag){return false;}
            stack.setTag(tag.isEmpty()?null:tag);
            if(stack.getMaxStackSize()!=s.maxCount() || s.count()>c.getMaxStackSize() || !c.canPlaceItem(i,stack))return false;
            newStacks[i]=stack;
        }
        // A write exception after this point is ambiguous: caller must quarantine both firms.
        for(int i=0;i<newStacks.length;i++)c.setItem(i,newStacks[i]);
        c.setChanged();
        cash.writeCopper(id,replacement.copper());
        return true;
    }
    private static boolean equal(TradeAtomicEconomy.Account a,TradeAtomicEconomy.Account b){
        if(!a.id().equals(b.id())||a.copper()!=b.copper()||a.slots().size()!=b.slots().size())return false;
        for(int i=0;i<a.slots().size();i++){
            var x=a.slots().get(i);var y=b.slots().get(i);
            if(x==null||y==null){if(x!=y)return false;}
            else if(x.count()!=y.count()||!x.sameItem(y))return false;
        }
        return true;
    }
}
