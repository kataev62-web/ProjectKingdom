package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Resource-backed lighting of a cleared mine gallery.
 * A real torch is taken from the enterprise's authorised raw warehouse;
 * it is never conjured into existence. No chunk is force-loaded.
 */
public final class KingdomMineMaintenance {
    private KingdomMineMaintenance() {}

    public enum Result { READY, WALKING_TO_SUPPLIES, NO_SUPPLIES, WALKING_TO_SITE, PLACED, BLOCKED }

    public static boolean lightPoint(BlockPos core, BlockPos target) {
        if(core==null||target==null||core.getY()!=target.getY())return false;
        // Main gallery and side-gallery centres only, every six blocks.
        int north=core.getZ()-target.getZ();
        int westEast=Math.abs(core.getX()-target.getX());
        return north>=6 && north<=KingdomMineGalleryPlan.MAIN_LENGTH
                && north%6==0 && westEast==0;
    }

    public static boolean passable(BlockState state) {
        return state.isAir() || state.is(Blocks.TORCH);
    }

    public static Result light(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                               BlockPos core,BlockPos at) {
        if(!lightPoint(core,at))return Result.READY;
        if(!level.hasChunkAt(at)||!level.hasChunkAt(at.below()))return Result.BLOCKED;
        BlockState current=level.getBlockState(at);
        if(current.is(Blocks.TORCH))return Result.READY;
        if(!current.isAir() || !level.getFluidState(at).isEmpty()
                || level.getBlockState(at.above()).isAir()==false
                || !Blocks.TORCH.defaultBlockState().canSurvive(level,at)
                || worker.employer==null
                || !worker.employer.equals(KingdomTerritories.get(level.getServer()).owner(level,at)))
            return Result.BLOCKED;

        // Carry one real torch from the warehouse to the gallery, even when
        // the warehouse and placement point are far apart.
        ItemStack carried=null;
        for(ItemStack item:worker.fieldInventory)
            if(item.is(Items.TORCH) && !item.isEmpty()){carried=item;break;}
        if(carried==null){
            if(!worker.hasSource)return Result.NO_SUPPLIES;
            BlockPos depot=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
            if(!level.hasChunkAt(depot)
                    ||!KingdomTerritories.get(level.getServer()).usable(level,depot,worker.employer,"raw"))
                return Result.NO_SUPPLIES;
            BlockEntity blockEntity=level.getBlockEntity(depot);
            if(!(blockEntity instanceof Container warehouse))return Result.NO_SUPPLIES;
            int slot=-1;
            for(int i=0;i<warehouse.getContainerSize();i++){
                ItemStack stack=warehouse.getItem(i);
                if(stack.is(Items.TORCH) && warehouse.canTakeItem(warehouse,i,stack)){
                    slot=i;break;
                }
            }
            if(slot<0)return Result.NO_SUPPLIES;
            if(npc.distanceToSqr(depot.getX()+0.5,depot.getY(),depot.getZ()+0.5)>6.25){
                npc.getNavigation().moveTo(depot.getX()+0.5,depot.getY(),depot.getZ()+0.5,0.75);
                return Result.WALKING_TO_SUPPLIES;
            }
            ItemStack taken=warehouse.removeItem(slot,1);
            if(!taken.is(Items.TORCH) || taken.getCount()!=1){
                if(!taken.isEmpty())NpcFieldWork.put(worker,taken);
                warehouse.setChanged();
                KingdomNpcData.get(level.getServer()).setDirty();
                return Result.BLOCKED;
            }
            NpcFieldWork.put(worker,taken);
            warehouse.setChanged();
            KingdomNpcData.get(level.getServer()).setDirty();
            return Result.WALKING_TO_SITE;
        }
        if(npc.distanceToSqr(at.getX()+0.5,at.getY(),at.getZ()+0.5)>6.25){
            npc.getNavigation().moveTo(at.getX()+0.5,at.getY(),at.getZ()+0.5,0.75);
            return Result.WALKING_TO_SITE;
        }
        // Revalidate at placement time; the selected site may have changed.
        if(!level.getBlockState(at).isAir()
                || !Blocks.TORCH.defaultBlockState().canSurvive(level,at))
            return Result.BLOCKED;
        if(!level.setBlock(at,Blocks.TORCH.defaultBlockState(),3))
            return Result.BLOCKED;
        carried.shrink(1);
        if(carried.isEmpty())worker.fieldInventory.remove(carried);
        KingdomNpcData.get(level.getServer()).setDirty();
        return Result.PLACED;
    }
}
