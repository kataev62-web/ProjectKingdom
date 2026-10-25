package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** C2S: selector index is verified against a server-owned catalog session. */
public record ArchitectCatalogActionPacket(UUID token,int action,int index,String copyName) {
    public static final int SELECT=0,PLACE=1,COPY=2;
    public static void encode(ArchitectCatalogActionPacket p,FriendlyByteBuf buf){
        buf.writeUUID(p.token);buf.writeByte(p.action);buf.writeVarInt(p.index);buf.writeUtf(p.copyName,48);
    }
    public static ArchitectCatalogActionPacket decode(FriendlyByteBuf buf){
        return new ArchitectCatalogActionPacket(buf.readUUID(),buf.readUnsignedByte(),
            buf.readVarInt(),buf.readUtf(48));
    }
    public static void handle(ArchitectCatalogActionPacket packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->{
            var player=ctx.getSender();
            if(player!=null)ru.projectkingdom.npc.KingdomArchitectCatalog.act(player,packet);
        });
        ctx.setPacketHandled(true);
    }
}
