package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** C2S only. A cancel clears selections of this player; it never grants authority. */
public record KingdomCancelVolumeSelectionPacket() {
    public static void encode(KingdomCancelVolumeSelectionPacket p,FriendlyByteBuf b){}
    public static KingdomCancelVolumeSelectionPacket decode(FriendlyByteBuf b){
        return new KingdomCancelVolumeSelectionPacket();
    }
    public static void handle(KingdomCancelVolumeSelectionPacket packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        if(ctx.getDirection()==NetworkDirection.PLAY_TO_SERVER){
            var player=ctx.getSender();
            if(player!=null)ctx.enqueueWork(()->{
                var id=player.getUUID();
                ru.projectkingdom.enterprise.EnterpriseBuildingSelection.cancel(id);
                ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(id);
                ru.projectkingdom.npc.KingdomStructureCopy.cancel(id);
            });
        }
        ctx.setPacketHandled(true);
    }
}
