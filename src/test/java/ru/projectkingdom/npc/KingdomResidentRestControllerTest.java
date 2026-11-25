package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

final class KingdomResidentRestControllerTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void criticalFatigueWaitsForRecoveryThreshold() {
        assertTrue(KingdomResidentRestController.fatigueRequired(false, 0));
        assertTrue(KingdomResidentRestController.fatigueRequired(false, 15));
        assertTrue(KingdomResidentRestController.fatigueRequired(false, 25));
        assertFalse(KingdomResidentRestController.fatigueRequired(false, 26));
        assertTrue(KingdomResidentRestController.fatigueRequired(true, 16));
        assertTrue(KingdomResidentRestController.fatigueRequired(true, 39));
        assertFalse(KingdomResidentRestController.fatigueRequired(true, 40));
    }

    @Test void walkingTowardABedDoesNotCountAsSleepOrStationaryRest() {
        assertEquals(0, KingdomResidentRestController.recoveryProgress(300, true, false, false));
        assertEquals(1, KingdomResidentRestController.recoveryProgress(0, true, false, true));
        assertEquals(4, KingdomResidentRestController.recoveryProgress(0, true, true, true));
    }

    @Test void unloadedTimeAndRestartCannotCreateRecoveryCredit() {
        assertEquals(0, KingdomResidentRestController.recoveryProgress(399, false, true, true));
        assertEquals(0, KingdomResidentRestController.recoveryProgress(399, false, false, true));
    }

    @Test void sleepingRecoversFasterThanObservedStationaryRest() {
        int sleeping = 0;
        int resting = 0;
        for (int tick = 0; tick < 100; tick++) {
            sleeping = KingdomResidentRestController.recoveryProgress(sleeping, true, true, true);
            resting = KingdomResidentRestController.recoveryProgress(resting, true, false, true);
        }
        assertEquals(400, sleeping);
        assertEquals(100, resting);
    }

    @Test void onlyAMatchingCompleteBedHasAValidHead() {
        var head = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(BedBlock.FACING, Direction.NORTH);
        var foot = head.setValue(BedBlock.PART, BedPart.FOOT);
        assertTrue(KingdomResidentRestController.validBedPair(head, foot));
        assertFalse(KingdomResidentRestController.validHead(foot));
        assertFalse(KingdomResidentRestController.validBedPair(head, Blocks.AIR.defaultBlockState()));
        assertFalse(KingdomResidentRestController.validBedPair(head, head));
        assertFalse(KingdomResidentRestController.validBedPair(head,
                foot.setValue(BedBlock.FACING, Direction.SOUTH)));
    }

    @Test void fatigueLatchSurvivesWorkerNbtRoundTripAndOldSavesDefaultSafely() {
        var worker = new KingdomNpcData.Worker(1, java.util.UUID.randomUUID(), "Rest test", "minecraft:overworld", 0);
        worker.energy = 30;
        worker.fatigueRestRequired = true;
        var saved = worker.save();
        var restored = KingdomNpcData.Worker.load(saved);
        assertTrue(KingdomResidentRestController.requiresRest(restored));
        saved.remove("FatigueRestRequired");
        assertFalse(KingdomNpcData.Worker.load(saved).fatigueRestRequired);
        restored.energy = 40;
        assertFalse(KingdomResidentRestController.requiresRest(restored));
    }
}
