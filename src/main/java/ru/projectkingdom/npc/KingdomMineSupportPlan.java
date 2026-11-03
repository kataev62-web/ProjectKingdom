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

    public record Frame(BlockPos leftPost, BlockPos rightPost, BlockPos crossbeam,
                        BlockPos walkway, int depth) {}

    /** At depth 5/10/15/20 of each level, posts are two blocks apart. */
    public static List<Frame> frames(BlockPos entrance) {
        if (entrance == null) return List.of();
        List<Frame> result = new ArrayList<>();
        for (int distance=SPACING; distance<=KingdomMineGalleryPlan.MAIN_LENGTH; distance+=SPACING) {
            BlockPos center=entrance.north(distance);
            result.add(new Frame(center.west(),center.east(),center.above(2),
                    center,distance));
        }
        return List.copyOf(result);
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
