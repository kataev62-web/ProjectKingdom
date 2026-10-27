package ru.projectkingdom.npc.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcDialogueSpeechTransportTest {
    @Test void repliesUseBoundedSpeechPacketInsteadOfListenerChat() throws Exception {
        String source=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/NpcDialogueService.java"));
        assertTrue(source.contains("new NpcSpeechPacket("));
        assertTrue(source.contains("NpcVoiceRecordings.durationTicks(voice,line)"));
        assertTrue(source.contains("KingdomNetwork.sendNpcSpeech(listener"));
        assertFalse(source.contains("listener.sendSystemMessage(subtitle)"));
    }

    @Test void directVoiceTargetIsResolvedServerSideByEntityUuid() throws Exception {
        String source=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/NpcDialogueService.java"));
        assertTrue(source.contains("respondVoice(ServerPlayer player, UUID targetEntity"));
        assertTrue(source.contains("entityId.equals(targetEntity)"));
        assertTrue(source.contains("return respond(player,worker,text)"));
    }
}
