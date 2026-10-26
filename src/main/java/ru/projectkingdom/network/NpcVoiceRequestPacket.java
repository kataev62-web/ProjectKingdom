package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.NpcDialogueService;
import ru.projectkingdom.npc.voice.NpcVoiceRateLimiter;

/** Client sends recognized text only. Raw microphone audio never crosses the network. */
public record NpcVoiceRequestPacket(UUID targetEntity,String text) {
    private static final NpcVoiceRateLimiter LIMITER=new NpcVoiceRateLimiter();

    public NpcVoiceRequestPacket {
        text=text==null?"":text;
    }

    public static void encode(NpcVoiceRequestPacket packet,FriendlyByteBuf buf){
        buf.writeBoolean(packet.targetEntity!=null);
        if(packet.targetEntity!=null)buf.writeUUID(packet.targetEntity);
        buf.writeUtf(packet.text,256);
    }

    public static NpcVoiceRequestPacket decode(FriendlyByteBuf buf){
        try {
            UUID target=buf.readBoolean()?buf.readUUID():null;
            return new NpcVoiceRequestPacket(target,buf.readUtf(256));
        } catch(RuntimeException malformed) {
            throw new IllegalArgumentException("Invalid NPC voice request",malformed);
        }
    }

    public static void handle(NpcVoiceRequestPacket packet,
                              Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        if(ctx.getDirection()!=NetworkDirection.PLAY_TO_SERVER){
            ctx.setPacketHandled(true);return;
        }
        var sender=ctx.getSender();
        if(sender==null){ctx.setPacketHandled(true);return;}
        ctx.enqueueWork(()->{
            var checked=LIMITER.validateAndRecord(sender.getUUID(),packet.text,
                    sender.getServer().getTickCount());
            if(checked.accepted())
                NpcDialogueService.respondVoice(sender,packet.targetEntity,checked.text());
        });
        ctx.setPacketHandled(true);
    }

    public static void forget(UUID playerId){LIMITER.forget(playerId);}
}
