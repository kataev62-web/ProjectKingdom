package ru.projectkingdom.npc.voice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.voice.client.VoiceAssetExtractor;

import static org.junit.jupiter.api.Assertions.*;

final class VoiceAssetExtractorTest {
    @Test void extractsVersionedModelOnceAndReusesCompletedCache() throws Exception {
        byte[] zip=zip("vosk-model-small-ru-0.22/conf/model.conf","данные");
        String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
        var root=Files.createTempDirectory("kingdom-voice-assets-");
        var first=VoiceAssetExtractor.extract(new ByteArrayInputStream(zip),root,
                "ru-0.22",checksum,"vosk-model-small-ru-0.22");
        assertEquals("данные",Files.readString(first.resolve("conf/model.conf")));
        Files.writeString(first.resolve("kept.txt"),"не извлекать повторно");
        var second=VoiceAssetExtractor.extract(new ByteArrayInputStream(zip),root,
                "ru-0.22",checksum,"vosk-model-small-ru-0.22");
        assertEquals(first,second);
        assertTrue(Files.exists(second.resolve("kept.txt")));
    }

    @Test void rejectsWrongChecksumBeforePublishingCache() throws Exception {
        byte[] zip=zip("vosk-model-small-ru-0.22/file","x");
        var root=Files.createTempDirectory("kingdom-voice-assets-");
        try {
            VoiceAssetExtractor.extract(new ByteArrayInputStream(zip),root,
                    "ru-0.22","0".repeat(64),"vosk-model-small-ru-0.22");
            fail("wrong checksum must fail");
        } catch(SecurityException expected) { }
        assertFalse(Files.exists(root.resolve("ru-0.22")));
    }

    @Test void rejectsZipSlipAbsoluteAndWindowsDriveEntries() throws Exception {
        for(String entry:new String[]{"../escape.txt","/absolute.txt","C:/windows.txt","..\\escape.txt"}){
            byte[] zip=zip(entry,"x");
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
            var root=Files.createTempDirectory("kingdom-voice-assets-");
            try {
                VoiceAssetExtractor.extract(new ByteArrayInputStream(zip),root,
                        "ru-0.22",checksum,"vosk-model-small-ru-0.22");
                fail("unsafe entry must fail: "+entry);
            } catch(SecurityException expected) { }
        }
    }

    private static byte[] zip(String path,String contents) throws Exception {
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry(path));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
