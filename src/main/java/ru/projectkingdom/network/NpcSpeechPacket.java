package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.voice.NpcVoiceBank;
import ru.projectkingdom.npc.voice.NpcVoiceLine;

/** Bounded server-approved speech event delivered only to nearby clients. */
public record NpcSpeechPacket(UUID entityId,String name,String details,String gender,int durationTicks, NpcVoiceBank voice,NpcVoiceLine line) {
    public NpcSpeechPacket {
        if(entityId==null)throw new IllegalArgumentException("NPC entity is required");
        name=name==null?"":name.strip();
        details=details==null?"":details.strip();
        gender=gender==null?"unspecified":gender.strip();
        if(name.isEmpty()||name.codePointCount(0,name.length())>64)
            throw new IllegalArgumentException("Invalid NPC speech name");
        if(details.codePointCount(0,details.length())>512)
            throw new IllegalArgumentException("Invalid NPC speech details");
        if(gender.codePointCount(0,gender.length())>16)
            throw new IllegalArgumentException("Invalid NPC speech gender");
        if(durationTicks<100||durationTicks>240)
            throw new IllegalArgumentException("Invalid NPC speech duration");
        if(voice==null||line==null)throw new IllegalArgumentException("Recorded NPC voice is required");
    }

    public String text(){return line.text();}

    public static void encode(NpcSpeechPacket packet,FriendlyByteBuf buf){
        buf.writeUUID(packet.entityId);
        buf.writeUtf(packet.name,64);buf.writeUtf(packet.details,512);buf.writeUtf(packet.gender,16);
        buf.writeVarInt(packet.durationTicks);
        buf.writeUtf(packet.voice.id(),16);buf.writeUtf(packet.line.id(),32);
    }

    public static NpcSpeechPacket decode(FriendlyByteBuf buf){
        try {
            return new NpcSpeechPacket(buf.readUUID(),buf.readUtf(64),buf.readUtf(512),
                    buf.readUtf(16),buf.readVarInt(),NpcVoiceBank.fromId(buf.readUtf(16)),NpcVoiceLine.fromId(buf.readUtf(32)));
        } catch(RuntimeException malformed) {
            throw new IllegalArgumentException("Invalid NPC speech packet",malformed);
        }
    }

    public static void handle(NpcSpeechPacket packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        if(ctx.getDirection()==NetworkDirection.PLAY_TO_CLIENT)
            ctx.enqueueWork(()->NpcSpeechClientBridge.accept(packet));
        ctx.setPacketHandled(true);
    }
}
