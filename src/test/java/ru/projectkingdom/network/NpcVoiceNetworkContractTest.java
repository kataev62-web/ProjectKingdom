package ru.projectkingdom.network;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcVoiceNetworkContractTest {
    @Test void targetedVoiceRequestSurvivesRoundTrip() {
        UUID target=UUID.randomUUID();
        var original=new NpcVoiceRequestPacket(target,"Как твои дела?");
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcVoiceRequestPacket.encode(original,buffer);
            assertEquals(original,NpcVoiceRequestPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void addressedVoiceRequestSurvivesRoundTripWithoutTarget() {
        var original=new NpcVoiceRequestPacket(null,"Анна, как дела?");
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcVoiceRequestPacket.encode(original,buffer);
            assertEquals(original,NpcVoiceRequestPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void speechPacketSurvivesRoundTripWithRussianText() {
        var original=new NpcSpeechPacket(UUID.randomUUID(),"Анна",
                "Я закончила работу и иду домой.","female",137);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcSpeechPacket.encode(original,buffer);
            assertEquals(original,NpcSpeechPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void maliciousOversizedVoiceTextIsRejectedDuringDecode() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(false);
            buffer.writeUtf("я".repeat(257));
            assertThrows(IllegalArgumentException.class,()->NpcVoiceRequestPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void invalidSpeechDurationIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new NpcSpeechPacket(
                UUID.randomUUID(),"Анна","Привет","female",99));
        assertThrows(IllegalArgumentException.class,()->new NpcSpeechPacket(
                UUID.randomUUID(),"Анна","Привет","female",161));
    }
}
