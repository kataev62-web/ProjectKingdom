package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class NpcCommunicationWiringTest {
    @Test void serviceEnforcesRangePersistsMemoryAndUsesPhysicalBread() throws Exception {
        String service = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/NpcDialogueService.java"));
        assertTrue(service.contains("CONVERSATION_RANGE_SQUARED = 64.0"));
        assertTrue(service.contains("LISTENER_RANGE_SQUARED = 256.0"));
        assertTrue(service.contains("player.distanceToSqr(npc) > CONVERSATION_RANGE_SQUARED"));
        assertTrue(service.contains("stack.is(Items.BREAD)"));
        assertTrue(service.contains("stack.shrink(1)"));
        assertTrue(service.contains("w.hunger = NpcLife.feed(w.hunger, 1)"));
        assertTrue(service.contains("w.remember(player.getUUID(), memory)"));
        assertTrue(service.contains("KingdomNetwork.sendNpcSpeech(listener,speech)"));
        assertFalse(service.contains("listener.sendSystemMessage"));
        assertTrue(service.contains("level.playSound"));
    }

    @Test void modWiresGreedyTalkCommandAndExplicitAddressedChatOnly() throws Exception {
        String mod = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        assertTrue(mod.contains("Commands.literal(\"talk\")"));
        assertTrue(mod.contains("StringArgumentType.greedyString()"));
        assertTrue(mod.contains("NpcDialogueService.respond("));
        assertTrue(mod.contains("public void addressedChat(ServerChatEvent event)"));
        assertTrue(mod.contains("NpcDialogueService.respondAddressed"));
        assertTrue(mod.contains("event.setCanceled(true)"));
        assertTrue(mod.contains("server.submit("));
        assertTrue(mod.contains(".join()"));
        assertFalse(mod.contains("import ru.projectkingdom.core.BindingInteractionPolicy;"));
    }

    @Test void tradeUsesCurrentCityMarketForMerchantAndWarehouse() throws Exception {
        String service = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/NpcDialogueService.java"));
        assertTrue(service.contains("\"merchant\".equals(w.job)"));
        assertTrue(service.contains("\"warehouse\".equals(w.job)"));
        assertTrue(service.contains("nearbyCityMarketTown"));
        assertFalse(service.contains("nearDesk("));
    }

    @Test void ordinaryTrackedNpcInteractionIsAlwaysDialogueFirst() throws Exception {
        String mod = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        assertFalse(mod.contains("KingdomWarehouseMarket.get(p.getServer()).nearDesk"));
        assertTrue(mod.contains("openNpcMenu(p,found.id);"));
    }
}

