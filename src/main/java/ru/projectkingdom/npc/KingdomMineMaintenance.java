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
        if(npc.distanceToSqr(at.getX()+0.5,at.getY(),at.getZ()+0.5)>6.25){
            npc.getNavigation().moveTo(at.getX()+0.5,at.getY(),at.getZ()+0.5,0.75);
            return Result.WALKING_TO_SITE;
        }
        // Stock could have changed during navigation. Recheck before withdrawal.
        if(npc.distanceToSqr(depot.getX()+0.5,depot.getY(),depot.getZ()+0.5)>6.25){
            npc.getNavigation().moveTo(depot.getX()+0.5,depot.getY(),depot.getZ()+0.5,0.75);
            return Result.WALKING_TO_SUPPLIES;
        }
        // A one-step physical transaction: mutate warehouse only after placement
        // succeeds. No speculative setBlock followed by a lost refund.
        ItemStack one=warehouse.removeItem(slot,1);
        if(!one.is(Items.TORCH) || one.getCount()!=1){
            if(!one.isEmpty()) {
                ItemStack existing=warehouse.getItem(slot);
                if(existing.isEmpty())warehouse.setItem(slot,one);
                else if(ItemStack.isSameItemSameTags(existing,one))existing.grow(one.getCount());
            }
            return Result.BLOCKED;
        }
        if(!level.setBlock(at,Blocks.TORCH.defaultBlockState(),3)){
            ItemStack remaining=warehouse.getItem(slot);
            if(remaining.isEmpty())warehouse.setItem(slot,one);
            else if(ItemStack.isSameItemSameTags(remaining,one))remaining.grow(1);
            else {
                // Inventory was externally replaced; keep the withdrawn item in
                // the NPC's persistent cargo instead of destroying it.
                NpcFieldWork.put(worker,one);
                KingdomNpcData.get(level.getServer()).setDirty();
            }
            warehouse.setChanged();
            return Result.BLOCKED;
        }
        warehouse.setChanged();
        KingdomNpcData.get(level.getServer()).setDirty();
        return Result.PLACED;
    }
}
