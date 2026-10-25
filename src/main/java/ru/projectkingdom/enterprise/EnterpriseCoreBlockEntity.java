package ru.projectkingdom.enterprise;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.npc.KingdomCurrency;

/**
 * Each enterprise core is its own physical coin vault. There is no external
 * cashbox binding for new companies. Coins persist in the core block entity NBT,
 * never in the business ledger alone and never exposed via a public item handler.
 */
public final class EnterpriseCoreBlockEntity extends BlockEntity implements Container {
    private static final int CASH_SLOTS=27;
    private final NonNullList<ItemStack> cash=NonNullList.withSize(CASH_SLOTS,ItemStack.EMPTY);
    private UUID enterpriseId;
    private UUID owner;
    private EnterpriseKind kind=EnterpriseKind.LEGACY;

    public EnterpriseCoreBlockEntity(BlockPos pos,BlockState state){
        super(KingdomEnterpriseRegistry.CORE_ENTITY.get(),pos,state);
    }
    public void bind(UUID enterpriseId,UUID owner,EnterpriseKind kind){
        if(this.enterpriseId!=null||enterpriseId==null||owner==null||kind==null||!kind.placeable())return;
        this.enterpriseId=enterpriseId;this.owner=owner;this.kind=kind;setChanged();
    }
    public UUID enterpriseId(){return enterpriseId;}
    public UUID owner(){return owner;}
    public EnterpriseKind kind(){return kind;}
    public boolean bound(){return enterpriseId!=null&&owner!=null&&kind.placeable();}
    @Override public int getContainerSize(){return CASH_SLOTS;}
    @Override public boolean isEmpty(){
        for(ItemStack stack:cash)if(!stack.isEmpty())return false;
        return true;
    }
    @Override public ItemStack getItem(int slot){
        return slot>=0&&slot<CASH_SLOTS?cash.get(slot):ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(int slot,int amount){
        if(slot<0||slot>=CASH_SLOTS||amount<1)return ItemStack.EMPTY;
        ItemStack extracted=ContainerHelper.removeItem(cash,slot,amount);
        if(!extracted.isEmpty())setChanged();
        return extracted;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){
        if(slot<0||slot>=CASH_SLOTS)return ItemStack.EMPTY;
        ItemStack removed=ContainerHelper.takeItem(cash,slot);
        if(!removed.isEmpty())setChanged();
        return removed;
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(slot<0||slot>=CASH_SLOTS)return;
        if(!stack.isEmpty()&&(!canPlaceItem(slot,stack)||stack.getCount()>getMaxStackSize()))
            throw new IllegalArgumentException("Enterprise vault accepts only coin ItemStacks");
        cash.set(slot,stack.copy());
        setChanged();
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        return slot>=0&&slot<CASH_SLOTS
            &&(stack.is(KingdomCurrency.COPPER.get()) || stack.is(KingdomCurrency.SILVER.get())
                 || stack.is(KingdomCurrency.GOLD.get()));
    }
    @Override public boolean stillValid(Player player){
        return bound()&&level!=null&&level.getBlockEntity(worldPosition)==this
            &&player.distanceToSqr(worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5)<=64;
    }
    @Override public void clearContent(){
        for(int i=0;i<CASH_SLOTS;i++)cash.set(i,ItemStack.EMPTY);
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);
        if(enterpriseId!=null)tag.putUUID("Enterprise",enterpriseId);
        if(owner!=null)tag.putUUID("Owner",owner);
        tag.putString("Kind",kind.id());
        CompoundTag purse=new CompoundTag();
        ContainerHelper.saveAllItems(purse,cash);
        tag.put("PhysicalCash",purse);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);
        enterpriseId=tag.hasUUID("Enterprise")?tag.getUUID("Enterprise"):null;
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        kind=EnterpriseKind.byId(tag.getString("Kind"));
        for(int i=0;i<CASH_SLOTS;i++)cash.set(i,ItemStack.EMPTY);
        if(tag.contains("PhysicalCash",10))
            ContainerHelper.loadAllItems(tag.getCompound("PhysicalCash"),cash);
    }
}
