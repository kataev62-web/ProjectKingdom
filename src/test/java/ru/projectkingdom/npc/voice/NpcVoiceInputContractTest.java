package ru.projectkingdom.npc.voice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcVoiceInputContractTest {
    @Test void keyIsRemappablePushToTalkAndSendsRecognizedTextOnly() throws Exception {
        String key=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceKeyHandler.java"));
        assertTrue(key.contains("GLFW.GLFW_KEY_V"));
        assertTrue(key.contains("RegisterKeyMappingsEvent"));
        assertTrue(key.contains("KEY.isDown()"));
        assertTrue(key.contains("capture.start()"));
        assertTrue(key.contains("capture.stop()"));
        assertTrue(key.contains("new NpcVoiceRequestPacket(selected.orElse(null),text)"));
        assertFalse(key.contains("byte[] audio"));
    }

    @Test void captureIsWindowsOnlySixteenKhzMonoAndCappedAtEightSeconds() throws Exception {
        String capture=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/WindowsMicrophoneCapture.java"));
        assertTrue(capture.contains("new AudioFormat(16000F,16,1,true,false)"));
        assertTrue(capture.contains("MAX_PCM_BYTES=16000*2*8"));
        assertTrue(capture.contains("os.name"));
        assertTrue(capture.contains("startsWith(\"Windows\")"));
    }

    @Test void targetingUsesEightBlockVillagerRayAndAddressFallback() throws Exception {
        String targeting=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceTargeting.java"));
        String key=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceKeyHandler.java"));
        assertTrue(targeting.contains("MAX_DISTANCE=8.0D"));
        assertTrue(targeting.contains("instanceof Villager"));
        assertTrue(key.contains("Optional<UUID> target"));
        assertTrue(key.contains("selected.orElse(null)"));
    }

    @Test void officialModelPartsReconstructThePinnedRussianArchive() throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        long size=0;
        for(int index=1;index<=5;index++){
            Path part=Path.of("voice-model/vosk-model-small-ru-0.22.zip.part%02d".formatted(index));
            byte[] bytes=Files.readAllBytes(part);
            size+=bytes.length;digest.update(bytes);
        }
        assertEquals(46236750L,size);
        assertEquals("961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4",
                HexFormat.of().formatHex(digest.digest()));
    }

    @Test void officialRussianModelAndVoskRuntimeAreEmbeddedAndPinned() throws Exception {
        Path model=Path.of("build/generated/voice/"
                +"vosk-model-small-ru-0.22.zip");
        assertEquals(46236750L,Files.size(model));
        String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(model)));
        assertEquals("961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4",
                checksum);
        String build=Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("com.alphacephei:vosk:0.3.45"));
        assertTrue(build.contains("verifyVoskModel"));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/kingdomnpc/voice/"
                +"LICENSE-vosk-model.txt")));
    }
}
