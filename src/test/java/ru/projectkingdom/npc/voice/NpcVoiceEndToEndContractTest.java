package ru.projectkingdom.npc.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcVoiceEndToEndContractTest {
    @Test void voicePipelineEndsInBubbleAndSpatialSpeechWithoutNpcChat() throws Exception {
        String key=read("src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceKeyHandler.java");
        String request=read("src/main/java/ru/projectkingdom/network/NpcVoiceRequestPacket.java");
        String dialogue=read("src/main/java/ru/projectkingdom/npc/NpcDialogueService.java");
        String client=read("src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceClient.java");

        assertTrue(key.contains("capture.start()"));
        assertTrue(key.contains("thenCompose(recognizer::recognize)"));
        assertTrue(key.contains("new NpcVoiceRequestPacket"));
        assertTrue(request.contains("NpcDialogueService.respondVoice"));
        assertTrue(dialogue.contains("KingdomNetwork.sendNpcSpeech"));
        assertFalse(dialogue.contains("listener.sendSystemMessage(subtitle)"));
        assertTrue(client.contains("BUBBLES.put("));
        assertFalse(client.contains("synthesize("));
        assertTrue(client.contains("new NpcRecordedSound("));
        assertTrue(client.contains("minecraft.getSoundManager().play(sound)"));
    }

    @Test void releaseDocumentationCoversOneJarWindowsFallbacks() throws Exception {
        String documentation=read("NPC_VOICE_WINDOWS_TEST_RU.md");
        assertTrue(documentation.contains("один JAR"));
        assertTrue(documentation.contains("клавишу V"));
        assertTrue(documentation.contains("первом запуске"));
        assertTrue(documentation.contains("микрофон"));
        assertTrue(documentation.contains("облако"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
