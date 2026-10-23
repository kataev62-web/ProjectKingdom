package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomLandMapMenu;

/** Read-only atlas link to the existing server-controlled land office map. */
public final class OpenLandMapFromAtlasPacket {
    public static void encode(OpenLandMapFromAtlasPacket p,FriendlyByteBuf buf) {}
    public static OpenLandMapFromAtlasPacket decode(FriendlyByteBuf buf) {
        return new OpenLandMapFromAtlasPacket();
    }
    public static void handle(OpenLandMapFromAtlasPacket p,Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();
        ctx.enqueueWork(()->{
            var viewer=ctx.getSender();
            if(viewer==null || viewer.getServer()==null)return;
            KingdomLandMapMenu.open(viewer);
        });
        ctx.setPacketHandled(true);
    }
}
