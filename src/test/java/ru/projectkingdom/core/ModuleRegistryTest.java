package ru.projectkingdom.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Test void failedModuleDoesNotPreventFollowingModuleFromStarting() {
        AtomicBoolean healthyStarted = new AtomicBoolean();
        ModuleRegistry modules = new ModuleRegistry()
                .register("broken", () -> { throw new IllegalStateException("boom"); }, true)
                .register("healthy", () -> healthyStarted.set(true), true)
                .register("future", () -> fail("disabled module started"), false);
        modules.startAll();
        assertEquals(ModuleRegistry.State.FAILED, modules.state("broken"));
        assertEquals(ModuleRegistry.State.ACTIVE, modules.state("healthy"));
        assertEquals(ModuleRegistry.State.DISABLED, modules.state("future"));
        assertTrue(healthyStarted.get());
        assertEquals(1, modules.failures().size());
    }
}
