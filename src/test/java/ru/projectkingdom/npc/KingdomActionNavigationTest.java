package ru.projectkingdom.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomActionNavigationTest {
    @Test void adjacentCompletedPathDoesNotProveSafeWorkStanceWasReached() {
        BlockPos stance=new BlockPos(39,200,41);
        // Native createPath(...,accuracy=1) may mark this adjacent endpoint
        // reachable; the miner must not accept it as the exact safe stance.
        Path adjacent=new Path(List.of(new Node(39,200,42)),stance,true);
        assertFalse(KingdomActionEngine.reaches(adjacent,stance));
        Path exact=new Path(List.of(new Node(39,200,42),new Node(39,200,41)),stance,true);
        assertTrue(KingdomActionEngine.reaches(exact,stance));
    }
    @Test void inaccessibleAndMissingPathsNeverStartMining() {
        BlockPos stance=new BlockPos(39,200,41);
        assertFalse(KingdomActionEngine.reaches(null,stance));
        assertFalse(KingdomActionEngine.reaches(new Path(List.of(new Node(39,200,41)),stance,false),stance));
        assertFalse(KingdomActionEngine.reaches(new Path(List.of(),stance,true),stance));
    }
}
