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
        assertTrue(service.contains("listener.sendSystemMessage"));
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
    }
}

