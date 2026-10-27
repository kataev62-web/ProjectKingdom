package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Architect and maps workflows may be developed in parallel. Packet IDs must never collide. */
class KingdomNetworkPacketRegistryTest {
    @Test void everySimpleChannelPacketHasUniqueId() throws Exception {
        String source=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/network/KingdomNetwork.java"));
        var m=Pattern.compile("CHANNEL\\.registerMessage\\((\\d+),").matcher(source);
        var used=new HashSet<Integer>();
        int count=0;
        while(m.find()){
            int id=Integer.parseInt(m.group(1));
            assertTrue(used.add(id),"Duplicate Kingdom packet id "+id);
            count++;
        }
        assertTrue(count>=21);
        assertTrue(source.contains("private static final String PROTOCOL=\"10\""),
                "Admin UI, voice and current menus require matching packet version 10");
        assertTrue(source.contains("40,NpcVoiceRequestPacket.class"));
        assertTrue(source.contains("41,NpcSpeechPacket.class"));
    }
}
