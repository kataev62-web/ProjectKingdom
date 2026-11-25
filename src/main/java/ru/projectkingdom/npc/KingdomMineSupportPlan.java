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

    public record Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                        BlockPos walkway, int depth) {
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
                center.above(2),center,distance));
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
        if(horizontal<1 || horizontal>2 || dx!=0 && dz!=0 || down<0 || down>1)
            return null;
        // TFC's pinned native support volume reaches only TWO blocks down.
        // From the layer above, link the middle of three native posts rather
        // than their tops. Move this low frame behind the safe approach so
        // the miner can stand outside it without digging collapse-risk pockets.
        BlockPos base=down==1?approach.offset(-Integer.signum(dx),0,-Integer.signum(dz)):approach;
        BlockPos beam=base.above(2-down);
        return dx!=0
            ?new Frame(base.north(),base.south(),beam,base,0)
            :new Frame(base.west(),base.east(),beam,base,0);
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

    /** A support item and two real vertical posts must exist before linking. */
    public static boolean supports(String itemId) {
        return itemId!=null && itemId.startsWith("tfc:wood/support/")
                && itemId.length()>"tfc:wood/support/".length();
    }
}
