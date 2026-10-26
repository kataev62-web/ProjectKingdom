package ru.projectkingdom.npc.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcSpeechBubbleContractTest {
    @Test void rendererUsesWorldSpaceWrappedHighContrastBubbleWithoutChat() throws Exception {
        String source=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/NpcSpeechBubbleRenderer.java"));
        assertTrue(source.contains("RenderLivingEvent.Post"));
        assertTrue(source.contains("instanceof Villager"));
        assertTrue(source.contains("cameraOrientation()"));
        assertTrue(source.contains("font.split(Component.literal(speech.text()),220)"));
        assertTrue(source.contains("0xFFD7A33D"));
        assertTrue(source.contains("0xFFFFFFFF"));
        assertFalse(source.contains("sendSystemMessage"));
        assertFalse(source.contains("displayClientMessage"));
    }

    @Test void clientSetupInstallsSpeechSinkAndDisconnectClearsState() throws Exception {
        String setup=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/core/KingdomBookClient.java"));
        String client=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceClient.java"));
        assertTrue(setup.contains("NpcSpeechClientBridge.install(NpcVoiceClient::acceptSpeech)"));
        assertTrue(client.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(client.contains("BUBBLES.clear()"));
    }
}
