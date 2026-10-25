package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** Bounded server-approved speech event delivered only to nearby clients. */
public record NpcSpeechPacket(UUID entityId,String name,String text,String gender,int durationTicks) {
    public NpcSpeechPacket {
        if(entityId==null)throw new IllegalArgumentException("NPC entity is required");
        name=name==null?"":name.strip();
        text=text==null?"":text.strip();
        gender=gender==null?"unspecified":gender.strip();
        if(name.isEmpty()||name.codePointCount(0,name.length())>64)
            throw new IllegalArgumentException("Invalid NPC speech name");
        if(text.isEmpty()||text.codePointCount(0,text.length())>512)
            throw new IllegalArgumentException("Invalid NPC speech text");
        if(gender.codePointCount(0,gender.length())>16)
            throw new IllegalArgumentException("Invalid NPC speech gender");
        if(durationTicks<100||durationTicks>160)
            throw new IllegalArgumentException("Invalid NPC speech duration");
    }

    public static void encode(NpcSpeechPacket packet,FriendlyByteBuf buf){
        buf.writeUUID(packet.entityId);
        buf.writeUtf(packet.name,64);buf.writeUtf(packet.text,512);buf.writeUtf(packet.gender,16);
        buf.writeVarInt(packet.durationTicks);
    }

    public static NpcSpeechPacket decode(FriendlyByteBuf buf){
        try {
            return new NpcSpeechPacket(buf.readUUID(),buf.readUtf(64),buf.readUtf(512),
                    buf.readUtf(16),buf.readVarInt());
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
