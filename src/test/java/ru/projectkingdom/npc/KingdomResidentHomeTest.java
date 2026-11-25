package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class KingdomResidentHomeTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static KingdomNpcData.Worker worker() {
        var worker = new KingdomNpcData.Worker(1, UUID.randomUUID(), "Resident", "minecraft:overworld", 0);
        worker.hasHome = true;
        worker.homeX = 100;
        worker.homeY = 70;
        worker.homeZ = 100;
        return worker;
    }

    @Test void savesTheRegisteredBedHeadInsteadOfThePlayersCoordinates() {
        var worker = worker();
        var head = new BlockPos(4, 64, 2);
        assertTrue(KingdomResidentHome.commitRegisteredHome(worker, Optional.empty(),
                Optional.of(GlobalPos.of(Level.OVERWORLD, head)), Level.OVERWORLD,
                new BlockPos(0, 64, 0), true));
        assertEquals(4, worker.homeX);
        assertEquals(64, worker.homeY);
        assertEquals(2, worker.homeZ);
    }

    @Test void rejectedOrUnchangedNativeRegistrationKeepsTheExistingHome() {
        var worker = worker();
        var previous = Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(100, 70, 100)));
        assertFalse(KingdomResidentHome.commitRegisteredHome(worker, previous, previous,
                Level.OVERWORLD, new BlockPos(100, 70, 100), true));
        assertFalse(KingdomResidentHome.commitRegisteredHome(worker, previous, Optional.empty(),
                Level.OVERWORLD, BlockPos.ZERO, true));
        assertTrue(worker.hasHome);
        assertEquals(100, worker.homeX);
        assertEquals(70, worker.homeY);
        assertEquals(100, worker.homeZ);
    }

    @Test void invalidDistantOrOtherDimensionBedCannotBecomeKingdomHome() {
        var worker = worker();
        var nearby = Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 1)));
        var player = new BlockPos(0, 64, 0);
        assertFalse(KingdomResidentHome.commitRegisteredHome(worker, Optional.empty(), nearby,
                Level.OVERWORLD, player, false));
        assertFalse(KingdomResidentHome.commitRegisteredHome(worker, Optional.empty(), nearby,
                Level.NETHER, player, true));
        assertFalse(KingdomResidentHome.commitRegisteredHome(worker, Optional.empty(),
                Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(9, 64, 0))),
                Level.OVERWORLD, player, true));
        assertEquals(100, worker.homeX);
        assertEquals(70, worker.homeY);
        assertEquals(100, worker.homeZ);
    }

    @Test void alreadyRegisteredNearbyBedCanSyncOldKingdomCoordinatesWithoutReclaimingPoi() {
        var worker = worker();
        var home = Optional.of(GlobalPos.of(Level.OVERWORLD,new BlockPos(1,64,1)));
        assertTrue(KingdomResidentHome.syncExistingHome(worker,home,Level.OVERWORLD,new BlockPos(0,64,0),true));
        assertEquals(1,worker.homeX);
        assertEquals(64,worker.homeY);
        assertFalse(KingdomResidentHome.syncExistingHome(worker,home,Level.OVERWORLD,new BlockPos(40,64,0),true));
        assertFalse(KingdomResidentHome.syncExistingHome(worker,home,Level.OVERWORLD,new BlockPos(0,64,0),false));
    }
}
