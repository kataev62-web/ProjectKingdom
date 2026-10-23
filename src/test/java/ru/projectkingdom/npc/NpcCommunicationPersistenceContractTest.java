package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class NpcCommunicationPersistenceContractTest {
    @Test void workerPersistsBackwardCompatiblePerPlayerSocialMemory() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcData.java"));
        assertTrue(source.contains("Map<UUID, NpcSocialMemory> socialMemory"));
        assertTrue(source.contains("public NpcSocialMemory social(UUID player)"));
        assertTrue(source.contains("public void remember(UUID player, NpcSocialMemory memory)"));
        assertTrue(source.contains("getList(\"SocialMemory\", Tag.TAG_COMPOUND)"));
        assertTrue(source.contains("t.put(\"SocialMemory\", social)"));
        assertTrue(source.contains("tag.putUUID(\"Player\""));
        assertTrue(source.contains("tag.putInt(\"Reputation\""));
        assertTrue(source.contains("tag.putString(\"LastTopic\""));
        assertTrue(source.contains("MAX_SOCIAL_MEMORIES = 512"));
        assertTrue(source.contains("socialMemory.remove(player)"));
        assertTrue(source.contains("while(socialMemory.size()>MAX_SOCIAL_MEMORIES)"));
        assertFalse(source.contains("social.size()&&i<512"));
    }
}

