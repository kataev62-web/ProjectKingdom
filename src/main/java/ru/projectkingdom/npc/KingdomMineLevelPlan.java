package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Bounded two-level mine: each level has a deterministic main gallery and
 * side branches; five adjacent descending cells connect the levels.
 *
 * Cells are ordered in travel order. A step drops by at most one Y block;
 * the physical executor validates the previous clear cell and solid floor
 * before breaking ANY block. No teleports or artificial item generation.
 */
public final class KingdomMineLevelPlan {
    private KingdomMineLevelPlan() {}
    public static final int LEVELS = 2;
    public static final int LEVEL_DROP = 4;
    public static final int DESCENT_RUN = 5;

    public static List<KingdomMineGalleryPlan.Cell> cells(BlockPos mineCore) {
        if(mineCore==null)return List.of();
        List<KingdomMineGalleryPlan.Cell> cells=new ArrayList<>();
        BlockPos anchor=mineCore;
        for(int level=0;level<LEVELS;level++){
            cells.addAll(KingdomMineGalleryPlan.cells(anchor));
            if(level+1>=LEVELS)break;
            BlockPos previous=anchor.north(KingdomMineGalleryPlan.MAIN_LENGTH);
            for(int step=1;step<=DESCENT_RUN;step++){
                // Descend four blocks along four successive horizontal steps,
                // then dig a level landing (fifth step).
                int drop=Math.min(step,LEVEL_DROP);
                BlockPos next=anchor.north(KingdomMineGalleryPlan.MAIN_LENGTH+step)
                    .below(drop);
                cells.add(new KingdomMineGalleryPlan.Cell(next,previous));
                previous=next;
            }
            // The landing is the entrance to the next gallery; the new core
            // is the last descending cell's SOUTH neighbour.
            anchor=previous.south();
        }
        return List.copyOf(cells);
    }
}
