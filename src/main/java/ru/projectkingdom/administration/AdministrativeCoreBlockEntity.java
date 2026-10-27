package ru.projectkingdom.administration;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AdministrativeCoreBlockEntity extends BlockEntity {
    private UUID townId;
    private UUID foundingPlacer;
    private UUID owner;
    private AdministrativeKind kind;
    public AdministrativeCoreBlockEntity(BlockPos pos,BlockState state){
        super(KingdomAdministrationRegistry.CORE_ENTITY.get(),pos,state);
    }
    /** The free-land town hall can only be founded by its original placer. */
    public void markFounding(UUID player){if(!bound()&&foundingPlacer==null&&player!=null){foundingPlacer=player;setChanged();}}
    public UUID placer(){return foundingPlacer;}
    public void bind(UUID townId,UUID owner,AdministrativeKind kind){
        if(bound()||townId==null||owner==null||kind==null)return;
        this.townId=townId;this.owner=owner;this.kind=kind;setChanged();
    }
    public UUID townId(){return townId;}
    public UUID owner(){return owner;}
    public AdministrativeKind kind(){return kind;}
    public boolean bound(){return townId!=null&&owner!=null&&kind!=null;}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);if(foundingPlacer!=null)tag.putUUID("FoundingPlacer",foundingPlacer);if(townId!=null)tag.putUUID("Town",townId);if(owner!=null)tag.putUUID("Owner",owner);
        if(kind!=null)tag.putString("Kind",kind.id());
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);foundingPlacer=tag.hasUUID("FoundingPlacer")?tag.getUUID("FoundingPlacer"):null;
        townId=tag.hasUUID("Town")?tag.getUUID("Town"):null;owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        kind=AdministrativeKind.byId(tag.getString("Kind")).orElse(null);
    }
}
