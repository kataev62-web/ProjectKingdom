package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * First native TFC support placement adapter. It never writes fake support
 * blockstates. TFC's real StandingAndWallBlockItem controls column creation
 * and the horizontal link. Every placement must be checked in the world.
 *
 * The two side alcoves must already be safely excavated. Excavating unstable
 * TFC rock to make those alcoves is NOT yet automated.
 */
public final class KingdomMineSupportBuilder {
    private KingdomMineSupportBuilder() {}

    public enum Result {
        COMPLETE, PLACED, SUPPLIED, WALKING, NO_STOCK, INACCESSIBLE,
        NEED_CLEAR_POCKETS, FAILED_NATIVE_PLACEMENT
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static boolean vertical(BlockState state) {
        return id(state).startsWith("tfc:wood/vertical_support/");
    }

    private static boolean horizontal(BlockState state) {
        return KingdomMineSupportSafety.isTfcHorizontalSupport(state);
    }

    /** A stray crossbeam without both standing supports is never complete. */
    private static boolean frameComplete(ServerLevel level,KingdomMineSupportPlan.Frame frame) {
        if(!horizontal(level.getBlockState(frame.crossbeam())))return false;
        for(BlockPos post:new BlockPos[]{frame.leftPost(),frame.rightPost()})
            for(int y=0;y<3;y++)
                if(!vertical(level.getBlockState(post.above(y))))return false;
        // Presence of the visual beam is insufficient; the authoritative TFC
        // support registry must recognize the protection. The frame protects
        // the rock of the walkway when the miner digs straight down and the
        // roof above the walkway when the corridor goes forward.
        return KingdomMineSupportSafety.hasHorizontalSupport(
            level,frame.protectedCell());
    }

    private static String type(ItemStack stack) {
        if(stack==null||stack.isEmpty())return "";
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return KingdomMineSupportPlan.supports(id)
            ?id.substring("tfc:wood/support/".length()):"";
    }

    private static boolean accessible(ServerLevel level,KingdomMineSupportPlan.Frame frame,
                                      KingdomNpcData.Worker worker) {
        if(frame==null||!KingdomMineSupportPlan.valid(frame)||worker.employer==null)
            return false;
        var land=KingdomTerritories.get(level.getServer());
        for(BlockPos column : new BlockPos[]{frame.leftPost(),frame.rightPost()}) {
            for(int dy=-1;dy<=2;dy++) {
                BlockPos position=column.above(dy);
                if(!level.hasChunkAt(position) || !land.canUse(level,position,worker.employer))
                    return false;
            }
        }
        return level.hasChunkAt(frame.crossbeam())
            && land.canUse(level,frame.crossbeam(),worker.employer);
    }

    private static boolean clearForPost(ServerLevel level,BlockPos base) {
        if(!level.getBlockState(base.below()).isFaceSturdy(
                level,base.below(),Direction.UP))
            return false;
        for(int y=0;y<=2;y++){
            BlockState s=level.getBlockState(base.above(y));
            if(!s.isAir()&&!vertical(s))return false;
            if(!level.getFluidState(base.above(y)).isEmpty())return false;
        }
        return true;
    }

    private static ItemStack carried(KingdomNpcData.Worker worker,int minimum) {
        for(ItemStack stack:worker.fieldInventory)
            if(!type(stack).isEmpty() && stack.getCount()>=minimum)return stack;
        return ItemStack.EMPTY;
    }

    private static Result supply(ServerLevel level,Villager npc,KingdomNpcData.Worker worker) {
        if(!worker.hasSource)return Result.NO_STOCK;
        BlockPos pos=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
        if(!level.hasChunkAt(pos)
            ||!KingdomTerritories.get(level.getServer()).usable(level,pos,worker.employer,"raw"))
            return Result.NO_STOCK;
        Container source=KingdomContainerAccess.resolve(level,pos);
        if(source==null)return Result.NO_STOCK;
        int slot=-1;
        for(int i=0;i<source.getContainerSize();i++){
            ItemStack stack=source.getItem(i);
            if(!type(stack).isEmpty() && stack.getCount()>=8
                && source.canTakeItem(source,i,stack)){slot=i;break;}
        }
        if(slot<0)return Result.NO_STOCK;
        if(npc.distanceToSqr(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5)>6.25){
            npc.getNavigation().moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0.75);
            return Result.WALKING;
        }
        ItemStack taken=source.removeItem(slot,8);
        if(taken.getCount()!=8 || type(taken).isEmpty()){
            if(!taken.isEmpty())NpcFieldWork.put(worker,taken);
            source.setChanged();
            KingdomNpcData.get(level.getServer()).setDirty();
            return Result.NO_STOCK;
        }
        NpcFieldWork.put(worker,taken);
        source.setChanged();
        KingdomNpcData.get(level.getServer()).setDirty();
        return Result.SUPPLIED;
    }

    private static Result nativePlace(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                                      BlockPos expected,Direction face,
                                      boolean bridge,BlockPos access) {
        ItemStack support=carried(worker,bridge?2:3);
        if(support.isEmpty())return Result.NO_STOCK;
        if(!KingdomActionEngine.inReach(npc,expected)) {
            npc.getNavigation().moveTo(access.getX()+0.5,access.getY(),access.getZ()+0.5,0.75);
            return Result.WALKING;
        }
        int before=support.getCount();
        var outcome=KingdomActionEngine.placeBlock(level,npc,worker,expected,support,
            face.getOpposite());
        if(!outcome.done())return outcome.status()==KingdomActionEngine.Status.MOVING
            ?Result.WALKING:Result.FAILED_NATIVE_PLACEMENT;
        boolean placed=bridge?horizontal(level.getBlockState(expected))
            :vertical(level.getBlockState(expected))
                &&vertical(level.getBlockState(expected.above()))
                &&vertical(level.getBlockState(expected.above(2)));
        int consumed=before-support.getCount();
        if(support.isEmpty())worker.fieldInventory.remove(support);
        KingdomNpcData.get(level.getServer()).setDirty();
        // TFC can reject a placement; never invent missing beam or silently retry
        // with fictional materials.
        return placed && consumed>0 ? Result.PLACED : Result.FAILED_NATIVE_PLACEMENT;
    }

    /** Use the same orientation for preparation, placement and completion checks. */
    static KingdomMineSupportPlan.Frame resolveFrame(ServerLevel level,
            KingdomMineSupportPlan.Frame frame,KingdomNpcData.Worker worker) {
        if(frame==null||!KingdomMineSupportPlan.valid(frame))return frame;
        if(frame.depth()==0) {
            // Preparing the default side alcoves can require breaking the very
            // unsupported rock this beam is meant to protect. Use an already
            // open pose behind the frontier instead; excavation stays fail-closed.
            for(var candidate:KingdomMineSupportPlan.nearbyFrames(frame)) {
                if(!accessible(level,candidate,worker)
                    ||!clearForPost(level,candidate.leftPost())
                    ||!clearForPost(level,candidate.rightPost())
                    ||!KingdomMineSupportSafety.plannedBeamCanSupport(
                        candidate.crossbeam(),candidate.protectedCell()))continue;
                BlockState beam=level.getBlockState(candidate.crossbeam());
                if(!beam.isAir()&&!frameComplete(level,candidate))continue;
                BlockPos access=safeAccess(level,candidate,worker);
                if(access!=null)return new KingdomMineSupportPlan.Frame(
                    candidate.leftPost(),candidate.rightPost(),candidate.crossbeam(),
                    candidate.walkway(),access,candidate.protectedCell(),candidate.depth());
            }
        }
        if(!obstructedByStructure(level,frame))return frame;
        boolean eastWest=frame.leftPost().getX()!=frame.rightPost().getX();
        BlockPos center=frame.walkway();
        var rotated=new KingdomMineSupportPlan.Frame(
            eastWest?center.north():center.west(),
            eastWest?center.south():center.east(),frame.crossbeam(),center,
            frame.access(),frame.protectedCell(),frame.depth());
        return obstructedByStructure(level,rotated)?frame:rotated;
    }

    private static BlockPos safeAccess(ServerLevel level,KingdomMineSupportPlan.Frame frame,
            KingdomNpcData.Worker worker) {
        var land=KingdomTerritories.get(level.getServer());
        java.util.List<BlockPos> candidates=new java.util.ArrayList<>();
        candidates.add(frame.access());
        candidates.add(frame.walkway());
        for(Direction direction:Direction.Plane.HORIZONTAL)
            candidates.add(frame.walkway().relative(direction));
        for(BlockPos pos:candidates) {
            if(pos.equals(frame.leftPost())||pos.equals(frame.rightPost())
                ||pos.equals(frame.crossbeam())||pos.above().equals(frame.crossbeam())
                ||pos.distSqr(frame.walkway())>4)continue;
            boolean safe=true;
            for(int dy=-1;dy<=1;dy++) {
                BlockPos cell=pos.above(dy);
                if(!level.hasChunkAt(cell)||!land.canUse(level,cell,worker.employer)) {
                    safe=false;
                    break;
                }
            }
            if(safe && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                &&KingdomMineMaintenance.passable(level.getBlockState(pos))
                &&KingdomMineMaintenance.passable(level.getBlockState(pos.above()))
                &&level.getFluidState(pos).isEmpty()&&level.getFluidState(pos.above()).isEmpty())return pos;
        }
        return null;
    }

    private static boolean obstructedByStructure(ServerLevel level,
            KingdomMineSupportPlan.Frame frame) {
        for(BlockPos post:new BlockPos[]{frame.leftPost(),frame.rightPost()})
            for(int y=0;y<3;y++) {
                BlockState state=level.getBlockState(post.above(y));
                if(state.is(net.minecraft.world.level.block.Blocks.LADDER)
                    ||KingdomMineStructureWork.isDeckBlock(state))return true;
            }
        return false;
    }

    public static Result tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                              KingdomMineSupportPlan.Frame frame) {
        frame=resolveFrame(level,frame,worker);
        if(!accessible(level,frame,worker))return Result.INACCESSIBLE;
        BlockPos left=frame.leftPost(),right=frame.rightPost(),middle=frame.crossbeam();
        if(frameComplete(level,frame))return Result.COMPLETE;
        if(horizontal(level.getBlockState(middle)))
            return Result.FAILED_NATIVE_PLACEMENT; // orphaned or damaged structure
        if(!clearForPost(level,left)||!clearForPost(level,right)
                ||!level.getBlockState(middle).isAir())
            return Result.NEED_CLEAR_POCKETS;
        // A broken/partial column is not a finished support. Don't add a
        // horizontal member to a structurally incomplete TFC installation.
        for(BlockPos post:new BlockPos[]{left,right}) {
            if(vertical(level.getBlockState(post))
                && (!vertical(level.getBlockState(post.above()))
                    || !vertical(level.getBlockState(post.above(2)))))
                return Result.FAILED_NATIVE_PLACEMENT;
        }
        boolean needPole=!vertical(level.getBlockState(left))
            ||!vertical(level.getBlockState(right));
        if(carried(worker,needPole?3:2).isEmpty())return supply(level,npc,worker);
        // An alternate cleared pose may put a future post on the miner's
        // previous staging cell. Leave both post columns before native use.
        for(BlockPos post:new BlockPos[]{left,right}) {
            if(npc.getBoundingBox().intersects(
                    new net.minecraft.world.phys.AABB(post).expandTowards(0,2,0))) {
                npc.getNavigation().moveTo(frame.access().getX()+.5,frame.access().getY(),
                    frame.access().getZ()+.5,.75);
                return Result.WALKING;
            }
        }
        // TFC places three vertical supports at once if enough real items are held.
        if(!vertical(level.getBlockState(left)))
            return nativePlace(level,npc,worker,left,Direction.UP,false,frame.access());
        if(!vertical(level.getBlockState(right)))
            return nativePlace(level,npc,worker,right,Direction.UP,false,frame.access());
        // A low emergency beam may overlap the miner's head. Native placement
        // must succeed from a separate safe square, never through the resident.
        if(frame.beamHeight()==1 && npc.getBoundingBox().intersects(
                new net.minecraft.world.phys.AABB(middle))) {
            BlockPos stand=null;
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                BlockPos candidate=frame.walkway().relative(direction);
                if(!level.hasChunkAt(candidate) || !level.hasChunkAt(candidate.below())
                    || !level.hasChunkAt(candidate.above())
                    || !KingdomMineMaintenance.passable(level.getBlockState(candidate))
                    || !KingdomMineMaintenance.passable(level.getBlockState(candidate.above()))
                    || !level.getFluidState(candidate).isEmpty()
                    || !level.getFluidState(candidate.above()).isEmpty()
                    || !level.getBlockState(candidate.below()).isFaceSturdy(level,candidate.below(),Direction.UP))
                    continue;
                if(stand==null || npc.distanceToSqr(candidate.getX()+.5,candidate.getY(),candidate.getZ()+.5)
                        <npc.distanceToSqr(stand.getX()+.5,stand.getY(),stand.getZ()+.5))stand=candidate;
            }
            if(stand==null)return Result.INACCESSIBLE;
            npc.getNavigation().moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5,.75);
            return Result.WALKING;
        }
        // Side-click a real post at the planned height. TFC owns the link.
        Direction span=left.getX()!=right.getX()?Direction.EAST:Direction.SOUTH;
        return nativePlace(level,npc,worker,middle,span,true,frame.access());
    }
}
