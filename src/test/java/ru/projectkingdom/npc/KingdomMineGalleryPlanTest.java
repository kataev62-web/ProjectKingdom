package ru.projectkingdom.npc;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineGalleryPlanTest {
    @Test void mainGalleryPrecedesSideBranchesAndHasNoDuplicateCells() {
        var core = new BlockPos(100, 64, 100);
        var cells = KingdomMineGalleryPlan.cells(core);
        assertEquals(71, cells.size());
        assertEquals(core.north(2), cells.get(0).target());
        assertEquals(core.north(1), cells.get(0).approach());
        assertEquals(core.north(24), cells.get(22).target());
        assertEquals(core.north(6).west(), cells.get(23).target());
        assertEquals(core.north(6), cells.get(23).approach());
        assertEquals(core.north(18).east(8), cells.get(70).target());
        assertEquals(71, new HashSet<>(cells.stream().map(KingdomMineGalleryPlan.Cell::target).toList()).size());
        assertTrue(cells.stream().allMatch(c -> c.target().getY()==64));
    }

    @Test void eachApproachIsTheEntranceOrEarlierCell() {
        var core = new BlockPos(-25, 42, 13);
        var cells = KingdomMineGalleryPlan.cells(core);
        var reachable = new HashSet<BlockPos>();
        reachable.add(core.north());
        for (var cell : cells) {
            assertTrue(reachable.contains(cell.approach()), "unreachable approach: "+cell);
            assertEquals(1, cell.target().distManhattan(cell.approach()), "nonadjacent step: "+cell);
            assertTrue(reachable.add(cell.target()), "duplicate target: "+cell);
        }
    }

    @Test void doesNotGenerateCellsWithoutAnAnchor() {
        assertTrue(KingdomMineGalleryPlan.cells(null).isEmpty());
    }
}
