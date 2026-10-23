package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomArchitectSessions;

/** C2S: client proposes only origin and quarter-turns, not blueprint block data. */
public record ArchitectDecisionPacket(UUID token,BlockPos origin,int rotation,boolean cancel) {
    public static void encode(ArchitectDecisionPacket p,FriendlyByteBuf buf){
        buf.writeUUID(p.token);
        buf.writeBlockPos(p.origin);
        buf.writeByte(p.rotation);
        buf.writeBoolean(p.cancel);
    }
    public static ArchitectDecisionPacket decode(FriendlyByteBuf buf){
        return new ArchitectDecisionPacket(buf.readUUID(),buf.readBlockPos(),
            buf.readByte(),buf.readBoolean());
    }
    public static void handle(ArchitectDecisionPacket packet,
                              Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->{
            var sender=ctx.getSender();
            if(sender!=null && sender.getServer()!=null)
                KingdomArchitectSessions.decide(sender,packet);
        });
        ctx.setPacketHandled(true);
    }
}
