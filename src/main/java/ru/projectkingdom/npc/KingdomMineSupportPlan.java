package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

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

    /**
     * One support frame of the mine.
     *
     * @param walkway the cell of the corridor the frame stands in and where
     *                the miner may stand
     * @param access the cell from which the miner may reach the frame
     * @param protectedCell the cell that must be supported by TFC: the roof
     *                      of the corridor when the miner goes forward, the
     *                      rock under its feet when it digs straight down
     */
    public record Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                        BlockPos walkway, BlockPos access, BlockPos protectedCell, int depth) {
        public Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                     BlockPos walkway, int depth) {
            this(leftPost, rightPost, crossbeam, walkway, walkway, walkway, depth);
        }
    }

    /** At depth 5/10/15/20 of each level, posts are two blocks apart. */
    public static List<Frame> frames(BlockPos entrance) {
        return frames(entrance,net.minecraft.core.Direction.NORTH,
            KingdomMineGalleryPlan.MAIN_LENGTH);
    }

    public static List<Frame> frames(BlockPos entrance,net.minecraft.core.Direction heading,
            int length) {
        if(entrance==null||heading==null||heading.getAxis().isVertical()
            ||length<SPACING)return List.of();
        List<Frame> result=new ArrayList<>();
        for(int distance=SPACING;distance<=length;distance+=SPACING){
            BlockPos center=entrance.relative(heading,distance);
            boolean eastWest=heading.getAxis()==net.minecraft.core.Direction.Axis.Z;
            result.add(new Frame(eastWest?center.west():center.north(),
                eastWest?center.east():center.south(),
                center.above(2),center,center,center.above(),distance));
        }
        return List.copyOf(result);
    }

    /** Choose a frame across the approach corridor, not across its direction. */
    public static Frame emergency(BlockPos approach,BlockPos target) {
        if(approach==null||target==null)return null;
        int dx=target.getX()-approach.getX();
        int dz=target.getZ()-approach.getZ();
        int dy=target.getY()-approach.getY();
        // The miner digs straight down: the rock under its feet stands in the
        // way of the ceiling beam of the walkway, so the frame stands around
        // the rock itself. The two posts are the neighbours of the rock, and
        // the beam links their tops.
        if(dx==0&&dz==0&&(dy==1||dy==-1)){
            // The miner digs straight down. The frame of the corridor fits:
            // the two posts stand on the rock beside the target and their
            // tops carry the beam. The rock under the miner is the cell that
            // must be supported.
            return new Frame(approach.north(),approach.south(),
                approach.above(2),approach,approach,target,0);
        }
        if(Math.abs(dx)+Math.abs(dz)!=1)return null;
        // Cross-beams span the corridor rather than obstructing its axis.
        // Both north-south and east-west tunnelling use TFC native placement.
        return dx!=0
            ?new Frame(approach.north(),approach.south(),
                approach.above(2),approach,approach,approach.above(),0)
            :new Frame(approach.west(),approach.east(),
                approach.above(2),approach,approach,approach.above(),0);
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
