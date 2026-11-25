package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One safe, real pickaxe action per work tick to clear a TFC support frame.
 * A dangerous TFC block must NEVER be excavated just to prepare supports.
 */
public final class KingdomMineSupportExcavation {
    private KingdomMineSupportExcavation() {}

    public enum Result { CLEAR, EXCAVATED, WALKING, UNSAFE }

    /** The last thing the preparation did, for the operator. */
    private static String lastDetail = "";
    /** The last thing the preparation did, for the operator. */
    public static String lastDetail() { return lastDetail; }

    public static Result tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                              KingdomMineSupportPlan.Frame frame) {
        frame=KingdomMineSupportBuilder.resolveFrame(level,frame);
        if(frame==null || !KingdomMineSupportPlan.valid(frame))return Result.UNSAFE;
        BlockPos[] positions={
            frame.leftPost().above(2),frame.rightPost().above(2),
            frame.crossbeam(),frame.leftPost().above(),frame.rightPost().above(),
            frame.leftPost(),frame.rightPost()
        };
        for(BlockPos pos:positions) {
            if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.above()))
                return Result.UNSAFE;
            BlockState block=level.getBlockState(pos);
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(block.getBlock()).toString();
            if(block.isAir()
                ||id.startsWith("tfc:wood/vertical_support/")
                ||KingdomMineSupportSafety.isTfcHorizontalSupport(block))
                continue;
            // The ladder and the deck of the descent are the finished
            // structures of the mine: the preparation of a support never
            // takes them apart.
            if(block.is(net.minecraft.world.level.block.Blocks.LADDER)
                ||KingdomMineStructureWork.isDeckBlock(block))return Result.UNSAFE;
            if(!KingdomMineTools.equip(level,npc,worker,
                    KingdomMineTools.required(block)))return Result.UNSAFE;
            if(!KingdomMineWork.safeToBreak(level,pos,worker)
                ||KingdomMineSupportSafety.inspect(level,pos)
                    !=KingdomMineSupportSafety.Result.SAFE)return Result.UNSAFE;
            // The miner works from the cell it may really stand on: the rock
            // of the walkway is not a place to stand when it digs downwards.
            BlockPos approach=frame.access();
            // The reach is measured against the block that has to go: the
            // miner may stand next to it. Only the way to the place is
            // planned, the reach of the arm decides the work.
            if(!KingdomActionEngine.inReach(npc,pos)) {
                npc.getNavigation().moveTo(approach.getX()+0.5,approach.getY(),
                    approach.getZ()+0.5,0.75);
                lastDetail="не дотягивается до "+pos.toShortString()
                    +" (доступ "+approach.toShortString()+")";
                return Result.WALKING;
            }
            lastDetail="расчищает "+pos.toShortString();
            var mined=KingdomActionEngine.breakBlock(level,npc,worker,pos,
                KingdomActionEngine.toolOf(npc,worker,KingdomMineTools.required(block)));
            if(!mined.done())return mined.status()==KingdomActionEngine.Status.MOVING
                ?Result.WALKING:Result.UNSAFE;
            return Result.EXCAVATED;
        }
        return Result.CLEAR;
    }
}
