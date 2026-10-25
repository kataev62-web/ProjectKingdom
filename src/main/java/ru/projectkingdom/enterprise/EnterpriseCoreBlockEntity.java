package ru.projectkingdom.enterprise;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.npc.KingdomCurrency;

/**
 * Physical enterprise till stored inside the company block NBT. It is NOT a
 * Container BlockEntity and exposes NO item capability: hoppers and visitors
 * cannot withdraw company money by bypassing the cashier's server-side checks.
 */
public final class EnterpriseCoreBlockEntity extends BlockEntity {
    private static final int CASH_SLOTS=27;
    private final SimpleContainer till=new SimpleContainer(CASH_SLOTS) {
        @Override public boolean canPlaceItem(int slot,ItemStack stack){
            return slot>=0&&slot<CASH_SLOTS
                    &&(stack.is(KingdomCurrency.COPPER.get())
                      ||stack.is(KingdomCurrency.SILVER.get())
                      ||stack.is(KingdomCurrency.GOLD.get()));
        }
        @Override public void setChanged(){
            super.setChanged();
            EnterpriseCoreBlockEntity.this.setChanged();
        }
    };
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
    /** Only trusted core services may use this view. Never expose it through a player menu slot. */
    public Container cashInventory(){return till;}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);
        if(enterpriseId!=null)tag.putUUID("Enterprise",enterpriseId);
        if(owner!=null)tag.putUUID("Owner",owner);
        tag.putString("Kind",kind.id());
        NonNullList<ItemStack> stacks=NonNullList.withSize(CASH_SLOTS,ItemStack.EMPTY);
        for(int i=0;i<CASH_SLOTS;i++)stacks.set(i,till.getItem(i).copy());
        CompoundTag physical=new CompoundTag();
        ContainerHelper.saveAllItems(physical,stacks);
        tag.put("PhysicalCash",physical);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);
        enterpriseId=tag.hasUUID("Enterprise")?tag.getUUID("Enterprise"):null;
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        kind=EnterpriseKind.byId(tag.getString("Kind"));
        NonNullList<ItemStack> items=NonNullList.withSize(CASH_SLOTS,ItemStack.EMPTY);
        if(tag.contains("PhysicalCash",10))
            ContainerHelper.loadAllItems(tag.getCompound("PhysicalCash"),items);
        for(int i=0;i<CASH_SLOTS;i++)till.setItem(i,items.get(i));
    }
}
