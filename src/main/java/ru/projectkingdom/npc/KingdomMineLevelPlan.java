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

    /** The starting ramp ends at the first lower-level entrance. */
    public static BlockPos firstAnchor(BlockPos mineCore,KingdomMineSettings.Start mode) {
        return mode==KingdomMineSettings.Start.DOWN
            ?mineCore.north(5).below(LEVEL_DROP):mineCore;
    }

    /** Gallery origins are needed for lighting and physical TFC frames. */
    public static List<BlockPos> anchors(BlockPos core,KingdomMineSettings.Start mode) {
        if(core==null||mode==null)return List.of();
        BlockPos first=firstAnchor(core,mode);
        return List.of(first,
            first.north(MAIN_DESCENT_START+DESCENT_RUN-1).below(LEVEL_DROP));
    }
    private static final int MAIN_DESCENT_START=KingdomMineGalleryPlan.MAIN_LENGTH;

    public static List<KingdomMineGalleryPlan.Cell> cells(BlockPos mineCore) {
        return cells(mineCore,KingdomMineSettings.Start.FORWARD);
    }

    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos mineCore,KingdomMineSettings.Start mode) {
        if(mineCore==null||mode==null)return List.of();
        List<KingdomMineGalleryPlan.Cell> cells=new ArrayList<>();
        BlockPos anchor=mineCore;
        if(mode==KingdomMineSettings.Start.DOWN) {
            // Entrance is mineCore.north(): it must already be passable.
            BlockPos previous=mineCore.north();
            for(int step=1;step<=DESCENT_RUN;step++){
                BlockPos next=mineCore.north(1+step)
                    .below(Math.min(step,LEVEL_DROP));
                cells.add(new KingdomMineGalleryPlan.Cell(next,previous));
                previous=next;
            }
            anchor=previous.south();
        }
        for(int level=0;level<LEVELS;level++){
            cells.addAll(KingdomMineGalleryPlan.cells(anchor));
            if(level+1>=LEVELS)break;
            BlockPos previous=anchor.north(MAIN_DESCENT_START);
            for(int step=1;step<=DESCENT_RUN;step++){
                int drop=Math.min(step,LEVEL_DROP);
                BlockPos next=anchor.north(MAIN_DESCENT_START+step).below(drop);
                cells.add(new KingdomMineGalleryPlan.Cell(next,previous));
                previous=next;
            }
            anchor=previous.south();
        }
        return List.copyOf(cells);
    }
}
