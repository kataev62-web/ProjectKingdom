package ru.projectkingdom.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end acceptance of the Stage 0 foundation without external launcher/payment providers. */
class Stage0GameCoreAcceptanceTest {
    @Test void identityBookAndFinanceSurviveTheExpectedPlayerJourney() {
        IdentitySavedData world = new IdentitySavedData();
        UUID firstSession = UUID.randomUUID();
        PlayerIdentity created = world.bind("launcher-account", firstSession).identity();
        assertEquals(IdentityRegistry.BindKind.RETURNING_SESSION,
                world.bind("launcher-account", firstSession).kind());

        IdentitySavedData reloaded = IdentitySavedData.load(world.save(new CompoundTag()));
        PlayerIdentity rebound = reloaded.bind("launcher-account", UUID.randomUUID()).identity();
        assertEquals(created.characterId(), rebound.characterId());
        assertEquals(created.dynastyId(), rebound.dynastyId());

        var book = BookModel.contents(rebound, new BookModel.Finance(1_250, 8_000));
        assertEquals(6, book.size());
        assertEquals(BookModel.Status.ACTIVE, book.get(1).status());
        assertTrue(book.stream().anyMatch(entry -> entry.status() == BookModel.Status.PLACEHOLDER));
    }

    @Test void unavailableFutureSystemsDegradeWithoutBlockingCoreAdmission() {
        var world = new SpawnResolver.SpawnPoint("minecraft:overworld", 0, 64, 0, "world");
        assertEquals(world, SpawnResolver.resolve(true, Optional.empty(), Optional.empty(), world));

        AtomicBoolean bookStarted = new AtomicBoolean();
        ModuleRegistry modules = new ModuleRegistry()
                .register("future-state", () -> { throw new IllegalStateException("not connected"); }, true)
                .register("book", () -> bookStarted.set(true), true);
        modules.startAll();
        assertTrue(bookStarted.get());
        assertEquals(ModuleRegistry.State.FAILED, modules.state("future-state"));
        assertFalse(PaymentProvider.disabled().requestTopUp(new IdentityIds.AccountId("account"), 100, "RUB").accepted());
    }
}
