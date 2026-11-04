package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;

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
        COMPLETE, PLACED, WALKING, NO_STOCK, INACCESSIBLE,
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
        return true;
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
        return Result.PLACED;
    }

    private static Result nativePlace(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                                      BlockPos clicked,Direction face,
                                      BlockPos expected,boolean bridge) {
        ItemStack support=carried(worker,bridge?2:3);
        if(support.isEmpty())return Result.NO_STOCK;
        if(npc.distanceToSqr(expected.getX()+0.5,expected.getY()+0.5,expected.getZ()+0.5)>6.25) {
            npc.getNavigation().moveTo(expected.getX()+0.5,expected.getY(),expected.getZ()+0.5,0.75);
            return Result.WALKING;
        }
        var fake=FakePlayerFactory.getMinecraft(level);
        if(fake.isCreative())return Result.FAILED_NATIVE_PLACEMENT;
        fake.setPos(npc.getX(),npc.getY(),npc.getZ());
        fake.setShiftKeyDown(false); // TFC places a complete three-block column when not sneaking.
        int before=support.getCount();
        fake.setItemInHand(InteractionHand.MAIN_HAND,support);
        try {
            var hit=new BlockHitResult(Vec3.atCenterOf(clicked),face,clicked,false);
            support.useOn(new UseOnContext(fake,InteractionHand.MAIN_HAND,hit));
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        }
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

    public static Result tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                              KingdomMineSupportPlan.Frame frame) {
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
        // TFC places three vertical supports at once if enough real items are held.
        if(!vertical(level.getBlockState(left)))
            return nativePlace(level,npc,worker,left.below(),Direction.UP,left,false);
        if(!vertical(level.getBlockState(right)))
            return nativePlace(level,npc,worker,right.below(),Direction.UP,right,false);
        // Side-click on the left top support, allowing TFC's horizontal rule
        // to link the right support via its real block-item implementation.
        return nativePlace(level,npc,worker,left.above(2),Direction.EAST,middle,true);
    }
}
