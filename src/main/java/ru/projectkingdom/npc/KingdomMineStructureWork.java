package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/** One real, owner-authorized construction operation per work tick. */
public final class KingdomMineStructureWork {
    private KingdomMineStructureWork(){}
    public enum Result { READY, BUILT, WALKING, NEED_MATERIAL, NEED_TOOL, UNSAFE }
    private static boolean plank(ItemStack s){
        if(s.isEmpty()||!(s.getItem() instanceof BlockItem))return false;
        String id=BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        return id.startsWith("minecraft:")&&id.endsWith("_planks")
            ||id.startsWith("tfc:wood/planks/");
    }
    private static boolean available(ItemStack s,boolean ladder){
        return ladder?s.is(Items.LADDER):plank(s);
    }
    private static ItemStack carried(KingdomNpcData.Worker w,boolean ladder){
        for(ItemStack s:w.fieldInventory)
            if(available(s,ladder))return s;
        return ItemStack.EMPTY;
    }
    private static Result acquire(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                  boolean ladder){
        if(w.employer==null||!w.hasSource)return Result.NEED_MATERIAL;
        BlockPos depot=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(!level.hasChunkAt(depot)
            ||!KingdomTerritories.get(level.getServer()).usable(level,depot,w.employer,"raw"))
            return Result.NEED_MATERIAL;
        Container c=KingdomContainerAccess.resolve(level,depot);
        if(c==null)return Result.NEED_MATERIAL;
        int slot=-1;
        for(int i=0;i<c.getContainerSize();i++)
            if(available(c.getItem(i),ladder)
                &&c.canTakeItem(c,i,c.getItem(i))){slot=i;break;}
        if(slot<0)return Result.NEED_MATERIAL;
        if(npc.distanceToSqr(depot.getX()+.5,depot.getY(),depot.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(depot.getX()+.5,depot.getY(),depot.getZ()+.5,.75);
            return Result.WALKING;
        }
        ItemStack picked=c.removeItem(slot,Math.min(32,c.getItem(slot).getCount()));
        if(!picked.isEmpty())NpcFieldWork.put(w,picked);
        c.setChanged();KingdomNpcData.get(level.getServer()).setDirty();
        return picked.isEmpty()?Result.NEED_MATERIAL:Result.WALKING;
    }
    private static boolean authorized(ServerLevel level,BlockPos at,KingdomNpcData.Worker w){
        return w.employer!=null&&level.hasChunkAt(at)
            &&w.employer.equals(KingdomTerritories.get(level.getServer()).owner(level,at));
    }
    private static Result ladder(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                 KingdomMineSettings.Shaft area,BlockPos at){
        if(!authorized(level,at,w)||!level.hasChunkAt(at.above()))return Result.UNSAFE;
        if(level.getBlockState(at).is(Blocks.LADDER))return Result.READY;
        if(!level.getBlockState(at).isAir())return Result.UNSAFE;
        BlockState placing=null;
        for(Direction wall:Direction.Plane.HORIZONTAL){
            // Only the OUTER wall is permanent. Inner walls become galleries.
            if(area!=null && area.contains(at.relative(wall)))continue;
            BlockState proposed=Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING,wall.getOpposite());
            if(level.hasChunkAt(at.relative(wall))&&proposed.canSurvive(level,at)){
                placing=proposed;break;
            }
        }
        if(placing==null)return Result.UNSAFE;
        ItemStack stock=carried(w,true);
        if(stock.isEmpty())return acquire(level,npc,w,true);
        if(npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.75);
            return Result.WALKING;
        }
        if(!level.setBlock(at,placing,3))return Result.UNSAFE;
        stock.shrink(1);
        if(stock.isEmpty())w.fieldInventory.remove(stock);
        KingdomNpcData.get(level.getServer()).setDirty();
        return Result.BUILT;
    }
    private static Result plank(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                BlockPos at) {
        if(!authorized(level,at,w)||!level.hasChunkAt(at.below())
            ||!authorized(level,at.above(),w))return Result.UNSAFE;
        BlockState state=level.getBlockState(at);
        String installed=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(installed.endsWith("_planks")||installed.startsWith("tfc:wood/planks/"))
            return Result.READY;
        ItemStack supply=carried(w,false);
        if(supply.isEmpty())return acquire(level,npc,w,false);
        BlockState result=((BlockItem)supply.getItem()).getBlock().defaultBlockState();
        if(!result.canSurvive(level,at)||!level.getFluidState(at).isEmpty())
            return Result.UNSAFE;
        if(npc.distanceToSqr(at.getX()+.5,at.getY()+1,at.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(at.getX()+.5,at.getY()+1,at.getZ()+.5,.75);
            return Result.WALKING;
        }
        if(!state.isAir()) {
            if(!KingdomMineTools.equip(level,npc,w,KingdomMineTools.required(state)))
                return Result.NEED_TOOL;
            if(!KingdomMineWork.safeToBreak(level,at,w)
                ||KingdomMineSupportSafety.inspect(level,at)
                    !=KingdomMineSupportSafety.Result.SAFE)return Result.UNSAFE;
            var drops=Block.getDrops(state,level,at,level.getBlockEntity(at),
                npc,w.fieldTool);
            if(!NpcFieldWork.canFit(w,drops))return Result.UNSAFE;
            if(!level.setBlock(at,Blocks.AIR.defaultBlockState(),3))return Result.UNSAFE;
            for(ItemStack drop:drops)NpcFieldWork.put(w,drop);
            w.fieldTool.hurt(1,level.random,null);
            if(w.fieldTool.isEmpty()||w.fieldTool.getDamageValue()>=w.fieldTool.getMaxDamage())
                w.fieldTool=ItemStack.EMPTY;
        }
        if(!level.setBlock(at,result,3))return Result.UNSAFE;
        supply.shrink(1);
        if(supply.isEmpty())w.fieldInventory.remove(supply);
        KingdomNpcData.get(level.getServer()).setDirty();
        return Result.BUILT;
    }
    public static Result ladderAt(ServerLevel level,Villager npc,
            KingdomNpcData.Worker worker,KingdomMineSettings.Shaft area,
            BlockPos landing) {
        return ladder(level,npc,worker,area,landing);
    }

    public static Result floorAt(ServerLevel level,Villager npc,
            KingdomNpcData.Worker worker,KingdomMineSettings.Shaft area,
            int depth,BlockPos core,int levels,
            KingdomMineSettings.Heading heading) {
        if(!KingdomMineStructurePlan.isLevel(depth))return Result.READY;
        for(BlockPos pos:KingdomMineStructurePlan.flooring(
                area,depth,core,levels,heading)) {
            Result result=plank(level,npc,worker,pos);
            if(result!=Result.READY)return result;
        }
        return Result.READY;
    }

    public static Result tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
            KingdomMineSettings.Shaft area,int depth,BlockPos landing){
        if(KingdomMineStructurePlan.requiresLadder(depth)) {
            Result installed=ladder(level,npc,w,area,landing);
            if(installed!=Result.READY)return installed;
        }
        if(KingdomMineStructurePlan.isLevel(depth))
            for(BlockPos at:KingdomMineStructurePlan.flooring(area,depth)){
                Result result=plank(level,npc,w,at);
                if(result!=Result.READY)return result;
            }
        return Result.READY;
    }
}