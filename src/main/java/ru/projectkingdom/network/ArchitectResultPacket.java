package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server says whether the building order was actually registered. */
public record ArchitectResultPacket(boolean accepted,String message) {
    public static void encode(ArchitectResultPacket packet,FriendlyByteBuf buf){
        buf.writeBoolean(packet.accepted);buf.writeUtf(packet.message,300);
    }
    public static ArchitectResultPacket decode(FriendlyByteBuf buf){
        return new ArchitectResultPacket(buf.readBoolean(),buf.readUtf(300));
    }
    public static void handle(ArchitectResultPacket packet,
                              Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            ()->()->ru.projectkingdom.npc.client.KingdomArchitectClient.result(packet)));
        ctx.setPacketHandled(true);
    }
}
