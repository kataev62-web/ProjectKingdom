package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Geometric plan for proper three-wide TFC mine support frames.
 * The central walkway stays clear. Two vertical posts stand outside it,
 * and the beam spanning them must be created by TFC's native placement.
 *
 * This planner does not place blocks or invent construction materials.
 */
public final class KingdomMineSupportPlan {
    private KingdomMineSupportPlan() {}
    public static final int SPACING = 5;

    public record Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                        BlockPos walkway, int depth) {}

    /** At depth 5/10/15/20 of each level, posts are two blocks apart. */
    public static List<Frame> frames(BlockPos entrance) {
        return frames(entrance,Direction.NORTH);
    }
    public static List<Frame> frames(BlockPos entrance,Direction forward) {
        if(entrance==null || forward==null || forward.getAxis()==Direction.Axis.Y)
            return List.of();
        List<Frame> result=new ArrayList<>();
        for(int distance=SPACING;distance<=KingdomMineGalleryPlan.MAIN_LENGTH;distance+=SPACING){
            BlockPos center=entrance.relative(forward,distance);
            result.add(new Frame(center.relative(forward.getCounterClockWise()),
                center.relative(forward.getClockWise()),center.above(2),center,distance));
        }
        return List.copyOf(result);
    }

    /** Choose a frame across the approach corridor, not across its direction. */
    public static Frame emergency(BlockPos approach,BlockPos target) {
        if(approach==null||target==null)return null;
        int dx=target.getX()-approach.getX();
        int dz=target.getZ()-approach.getZ();
        if(Math.abs(dx)+Math.abs(dz)!=1)return null;
        // For a descending stair, the crossbeam must remain within TFC's
        // support_up radius (2 blocks) of the LOWER target. A frame rooted at
        // the upper approach would put the beam three blocks above it.
        BlockPos base=target.getY()<approach.getY()?approach.below():approach;
        if(dx!=0)return new Frame(base.north(),base.south(),
            base.above(2),base,0);
        return new Frame(base.west(),base.east(),
            base.above(2),base,0);
    }

    public static boolean valid(Frame frame) {
        return frame!=null
                && frame.leftPost().getY()==frame.walkway().getY()
                && frame.rightPost().getY()==frame.walkway().getY()
                && frame.crossbeam().getY()==frame.walkway().getY()+2
                && frame.crossbeam().getX()==frame.walkway().getX()
                && frame.crossbeam().getZ()==frame.walkway().getZ()
                && frame.leftPost().distManhattan(frame.rightPost())==2
                && frame.leftPost().distManhattan(frame.walkway())==1
                && frame.rightPost().distManhattan(frame.walkway())==1;
    }

    /** A support item and two real vertical posts must exist before linking. */
    public static boolean supports(String itemId) {
        return itemId!=null && itemId.startsWith("tfc:wood/support/")
                && itemId.length()>"tfc:wood/support/".length();
    }
}
