package ru.projectkingdom.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class KingdomSettlementCharterEntity extends BlockEntity {
    private UUID placer;
    private UUID townId;
    public KingdomSettlementCharterEntity(BlockPos pos,BlockState state){
        super(KingdomSettlementRegistry.ENTITY.get(),pos,state);
    }
    public UUID placer(){return placer;}
    public UUID townId(){return townId;}
    public boolean founded(){return townId!=null;}
    public void placedBy(UUID player){
        if(player!=null&&placer==null){placer=player;setChanged();}
    }
    public void establish(UUID id){
        if(id!=null&&townId==null){townId=id;setChanged();}
    }
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);
        if(placer!=null)tag.putUUID("Placer",placer);
        if(townId!=null)tag.putUUID("Town",townId);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);
        placer=tag.hasUUID("Placer")?tag.getUUID("Placer"):null;
        townId=tag.hasUUID("Town")?tag.getUUID("Town"):null;
    }
}
