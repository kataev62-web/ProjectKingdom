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
                "Я закончила работу и иду домой.","female",137,ru.projectkingdom.npc.voice.NpcVoiceBank.FEMALE_2,ru.projectkingdom.npc.voice.NpcVoiceLine.WORKING);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcSpeechPacket.encode(original,buffer);
            assertEquals(original,NpcSpeechPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void automaticSpeechOriginSurvivesRoundTrip() {
        var packet=new NpcSpeechPacket(UUID.randomUUID(),"Анна","","female",100,
                ru.projectkingdom.npc.voice.NpcVoiceBank.FEMALE_1,
                ru.projectkingdom.npc.voice.NpcVoiceLine.HOW_DAY,true);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { NpcSpeechPacket.encode(packet,buffer);
            var decoded=NpcSpeechPacket.decode(buffer);assertTrue(decoded.automatic());
            assertEquals(packet,decoded);assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
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
                UUID.randomUUID(),"Анна","Привет","female",99,ru.projectkingdom.npc.voice.NpcVoiceBank.FEMALE_1,ru.projectkingdom.npc.voice.NpcVoiceLine.GREETING));
        assertThrows(IllegalArgumentException.class,()->new NpcSpeechPacket(
                UUID.randomUUID(),"Анна","Привет","female",241,ru.projectkingdom.npc.voice.NpcVoiceBank.FEMALE_1,ru.projectkingdom.npc.voice.NpcVoiceLine.GREETING));
    }
    @Test void everyRecordedVoiceAndLineRoundTripsWithoutTransmittingAudio() {
        for(var bank:ru.projectkingdom.npc.voice.NpcVoiceBank.values())for(var line:ru.projectkingdom.npc.voice.NpcVoiceLine.values()) {
            var original=new NpcSpeechPacket(new UUID(0,1),"Анна","Текущие сведения","female",100,bank,line);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { NpcSpeechPacket.encode(original,buffer);assertTrue(buffer.readableBytes()<200);
                assertEquals(original,NpcSpeechPacket.decode(buffer));assertEquals(0,buffer.readableBytes());
                assertEquals(line.text(),original.text());
            } finally {buffer.release();}
        }
    }
    @Test void unknownRecordingPathsAreRejected() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {buffer.writeUUID(UUID.randomUUID());buffer.writeUtf("Анна");buffer.writeUtf("Детали");
            buffer.writeUtf("female");buffer.writeVarInt(100);buffer.writeUtf("../../file");buffer.writeUtf("greeting");
            assertThrows(IllegalArgumentException.class,()->NpcSpeechPacket.decode(buffer));
        } finally {buffer.release();}
    }
}
