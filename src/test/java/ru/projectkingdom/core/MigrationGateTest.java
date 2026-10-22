package ru.projectkingdom.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MigrationGateTest {
    @Test void migrationsAreOrderedAndAdmissionOpensAtTarget() {
        AtomicInteger sequence = new AtomicInteger();
        MigrationGate gate = new MigrationGate(2)
                .step(1, () -> assertEquals(0, sequence.getAndIncrement()))
                .step(2, () -> assertEquals(1, sequence.getAndIncrement()));
        var result = gate.migrate(0);
        assertTrue(result.admitted());
        assertEquals(2, result.version());
    }

    @Test void failureAndVersionGapsKeepAdmissionClosed() {
        MigrationGate failed = new MigrationGate(1).step(1, () -> { throw new IllegalStateException("broken"); });
        assertFalse(failed.migrate(0).admitted());
        MigrationGate gap = new MigrationGate(2).step(2, () -> {});
        assertEquals("missing_migration_1", gap.migrate(0).failure());
    }
}
