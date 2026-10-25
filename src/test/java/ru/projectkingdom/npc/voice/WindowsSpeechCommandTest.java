package ru.projectkingdom.npc.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.voice.client.WindowsSpeechSynthesizer;

import static org.junit.jupiter.api.Assertions.*;

final class WindowsSpeechCommandTest {
    @Test void dangerousRussianTextIsDecodedAsDataAndNeverInsertedAsPowerShell() {
        String text="Привет'; Remove-Item C:\\\\*; $env:PATH; `n \"ещё\" & exit 9";
        Path output=Path.of("C:\\Temp\\реплика ' 1.wav");
        String encoded=WindowsSpeechSynthesizer.encodedCommand(text,"female",output);
        String script=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_16LE);
        String textData=Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String pathData=Base64.getEncoder().encodeToString(
                output.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));

        assertFalse(script.contains(text));
        assertFalse(script.contains(output.toAbsolutePath().toString()));
        assertTrue(script.contains("FromBase64String('"+textData+"')"));
        assertTrue(script.contains("FromBase64String('"+pathData+"')"));
        assertTrue(script.contains("System.Speech"));
        assertTrue(script.contains("SetOutputToWaveFile"));
    }

    @Test void processInvocationUsesEncodedCommandWithoutShellConcatenation() throws Exception {
        String source=java.nio.file.Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/voice/client/WindowsSpeechSynthesizer.java"));
        assertTrue(source.contains("new ProcessBuilder("));
        assertTrue(source.contains("\"-EncodedCommand\""));
        assertFalse(source.contains("powershell.exe -"));
        assertFalse(source.contains("Runtime.getRuntime().exec"));
    }
}
