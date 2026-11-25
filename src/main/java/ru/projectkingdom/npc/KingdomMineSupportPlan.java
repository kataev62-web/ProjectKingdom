package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Geometric plan for proper three-wide TFC mine support frames.
 * Tunnel frames leave a full-height central walkway. Emergency surface
 * frames can link the middle of the posts, with the miner staged outside
 * the low beam. Every beam must be created by TFC's native placement.
 *
 * This planner does not place blocks or invent construction materials.
 */
public final class KingdomMineSupportPlan {
    private KingdomMineSupportPlan() {}
    public static final int SPACING = 5;

    /**
     * One support frame of the mine.
     *
     * @param walkway the center cell of the frame; low emergency beams require
     *                the miner to stand outside it
     * @param access the cell from which the miner may reach the frame
     * @param protectedCell the cell that must be supported by TFC: the roof
     *                      of the corridor when the miner goes forward, the
     *                      rock under its feet when it digs straight down
     */
    public record Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                        BlockPos walkway, BlockPos access, BlockPos protectedCell, int depth) {
        public Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                     BlockPos walkway, int depth) {
            this(leftPost, rightPost, crossbeam, walkway, walkway, walkway.above(), depth);
        }
        public int beamHeight() { return crossbeam.getY()-walkway.getY(); }
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
        int horizontal=Math.abs(dx)+Math.abs(dz);
        int down=approach.getY()-target.getY();
        if(dx!=0 && dz!=0 || horizontal>2 || down< -1 || down>1
                || horizontal==0 && Math.abs(down)!=1
                || horizontal>0 && down<0)return null;
        // TFC's pinned native support volume reaches only TWO blocks down.
        // From the layer above, link the middle of three native posts rather
        // than their tops. Stage the low frame behind the safe approach so
        // the miner need not enter the low beam or cut unstable lower rock.
        BlockPos base=down==1
            ?(horizontal==0?approach.south():approach.offset(-Integer.signum(dx),0,-Integer.signum(dz)))
            :approach;
        BlockPos beam=base.above(down==1?1:2);
        return dx!=0 || horizontal==0 && down<0
            ?new Frame(base.north(),base.south(),beam,base,approach,target,0)
            :new Frame(base.west(),base.east(),beam,base,approach,target,0);
    }

    public static boolean valid(Frame frame) {
        return frame!=null
                && frame.leftPost().getY()==frame.walkway().getY()
                && frame.rightPost().getY()==frame.walkway().getY()
                && (frame.beamHeight()==1 || frame.beamHeight()==2)
                && frame.crossbeam().getX()==frame.walkway().getX()
                && frame.crossbeam().getZ()==frame.walkway().getZ()
                && frame.leftPost().distManhattan(frame.rightPost())==2
                && frame.leftPost().distManhattan(frame.walkway())==1
                && frame.rightPost().distManhattan(frame.walkway())==1;
    }

    /** Nearby emergency poses, nearest first; never change the protected rock or level. */
    static List<Frame> nearbyFrames(Frame frame) {
        if(!valid(frame))return List.of();
        List<Frame> result=new ArrayList<>();
        boolean eastWest=frame.leftPost().getX()!=frame.rightPost().getX();
        for(int distance=0;distance<=4;distance++)
            for(int dx=-distance;dx<=distance;dx++)
                for(int dz=-distance;dz<=distance;dz++) {
                    if(Math.abs(dx)+Math.abs(dz)!=distance)continue;
                    BlockPos center=frame.walkway().offset(dx,0,dz);
                    for(boolean acrossX:new boolean[]{eastWest,!eastWest})
                        result.add(new Frame(acrossX?center.west():center.north(),
                            acrossX?center.east():center.south(),center.above(frame.beamHeight()),
                            center,frame.access(),frame.protectedCell(),frame.depth()));
                }
        return List.copyOf(result);
    }

    /** A support item and two real vertical posts must exist before linking. */
    public static boolean supports(String itemId) {
        return itemId!=null && itemId.startsWith("tfc:wood/support/")
                && itemId.length()>"tfc:wood/support/".length();
    }
}
