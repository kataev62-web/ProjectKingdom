package ru.projectkingdom.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;

public final class KingdomSettlementMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final String title;
    private final boolean founded;
    public KingdomSettlementMenu(int id,Inventory inventory,FriendlyByteBuf data) {
        this(id,data.readBlockPos(),data.readUtf(80),data.readBoolean());
    }
    private KingdomSettlementMenu(int id,BlockPos pos,String title,boolean founded){
        super(KingdomSettlementRegistry.MENU.get(),id);
        this.pos=pos.immutable();this.title=title;this.founded=founded;
    }
    public BlockPos pos(){return pos;}
    public String title(){return title;}
    public boolean founded(){return founded;}
    public static void open(ServerPlayer player,KingdomSettlementCharterEntity core){
        if(player==null||core==null||!player.serverLevel().hasChunkAt(core.getBlockPos())
                ||core.getBlockPos().distSqr(player.blockPosition())>64)return;
        var civic=KingdomCivicsData.get(player.getServer());
        var town=core.townId()==null?null:civic.townById(core.townId());
        String name=town==null?"Новый хутор":
                KingdomSettlementGrowth.forTown(civic,KingdomConstructionData.get(player.getServer()),
                    town.id(),player.serverLevel().dimension().location().toString(),player.getServer()).title()+" «"+town.name()+"»";
        BlockPos pos=core.getBlockPos().immutable();
        boolean founded=core.founded();
        NetworkHooks.openScreen(player,new MenuProvider(){
            @Override public Component getDisplayName(){return Component.literal("Kingdom — Основание поселения");}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player ignored){
                return new KingdomSettlementMenu(id,pos,name,founded);
            }
        },buf->{buf.writeBlockPos(pos);buf.writeUtf(name,80);buf.writeBoolean(founded);});
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player) {
        if(!(player instanceof ServerPlayer server)||!player.isAlive()||pos.distSqr(player.blockPosition())>64
                ||!server.serverLevel().hasChunkAt(pos))return false;
        return server.serverLevel().getBlockEntity(pos) instanceof KingdomSettlementCharterEntity
                &&server.serverLevel().getBlockState(pos).is(KingdomSettlementRegistry.CHARTER.get());
    }
}
