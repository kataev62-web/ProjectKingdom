package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.map.KingdomHeldMapService;

/**
 * Route destination only: never sends privileges, money, ownership or a map ID.
 * The server saves the player's dimension and validates the coordinate bounds.
 */
public record SelectHeldMapDestinationPacket(int chunkX, int chunkZ) {
    public static void encode(SelectHeldMapDestinationPacket p,FriendlyByteBuf buf) {
        buf.writeInt(p.chunkX());buf.writeInt(p.chunkZ());
    }
    public static SelectHeldMapDestinationPacket decode(FriendlyByteBuf buf) {
        return new SelectHeldMapDestinationPacket(buf.readInt(),buf.readInt());
    }
    public static void handle(SelectHeldMapDestinationPacket p,Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();
        ctx.enqueueWork(()->{
            var player=ctx.getSender();
            if(player==null || player.getServer()==null) return;
            if(Math.abs((long)p.chunkX())>16384 || Math.abs((long)p.chunkZ())>16384)return;
            KingdomHeldMapService.selectDestination(player,p.chunkX(),p.chunkZ());
        });
        ctx.setPacketHandled(true);
    }
}
