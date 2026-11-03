package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Deterministic, bounded mine layout. A real mine core is the anchor.
 *
 * Phase 1: a 24-cell north gallery.
 * Phase 2: paired eight-cell side galleries at 6/12/18 blocks.
 *
 * Every cell carries the preceding reachable cell. The executor never
 * teleports an NPC, loads missing chunks, or excavates without a floor.
 * Layout is derived from the mine position, so no fake progress counter
 * can become desynchronised with the real world.
 */
public final class KingdomMineGalleryPlan {
    private KingdomMineGalleryPlan() {}

    public static final int MAIN_LENGTH = 24;
    public static final int BRANCH_LENGTH = 8;
    private static final int[] BRANCH_DEPTHS = {6, 12, 18};

    public record Cell(BlockPos target, BlockPos approach) {}

    public static List<Cell> cells(BlockPos mineCore) {
        if (mineCore == null) return List.of();
        List<Cell> plan = new ArrayList<>(MAIN_LENGTH - 1
                + BRANCH_DEPTHS.length * 2 * BRANCH_LENGTH);
        for (int distance = 2; distance <= MAIN_LENGTH; distance++)
            plan.add(new Cell(mineCore.north(distance),
                    mineCore.north(distance - 1)));

        for (int depth : BRANCH_DEPTHS) {
            BlockPos junction = mineCore.north(depth);
            for (int sign : new int[]{-1, 1}) {
                BlockPos previous = junction;
                for (int offset = 1; offset <= BRANCH_LENGTH; offset++) {
                    BlockPos target = junction.offset(sign * offset, 0, 0);
                    plan.add(new Cell(target, previous));
                    previous = target;
                }
            }
        }
        return List.copyOf(plan);
    }
}
