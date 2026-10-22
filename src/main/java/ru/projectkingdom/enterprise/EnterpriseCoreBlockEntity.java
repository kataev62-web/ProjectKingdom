package ru.projectkingdom.enterprise;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EnterpriseCoreBlockEntity extends BlockEntity {
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
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);
        if(enterpriseId!=null)tag.putUUID("Enterprise",enterpriseId);
        if(owner!=null)tag.putUUID("Owner",owner);
        tag.putString("Kind",kind.id());
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);enterpriseId=tag.hasUUID("Enterprise")?tag.getUUID("Enterprise"):null;
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;kind=EnterpriseKind.byId(tag.getString("Kind"));
    }
}
