package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomBorderOverlay;

/** No client coordinates or claims accepted: toggle only the sending player's view. */
public record ToggleCityBordersPacket() {
    public static void encode(ToggleCityBordersPacket packet,FriendlyByteBuf buffer){}
    public static ToggleCityBordersPacket decode(FriendlyByteBuf buffer){return new ToggleCityBordersPacket();}
    public static void handle(ToggleCityBordersPacket packet,Supplier<NetworkEvent.Context> contextSupplier){
        var context=contextSupplier.get();
        context.enqueueWork(()->{
            var sender=context.getSender();
            if(sender!=null)KingdomBorderOverlay.toggle(sender);
        });
        context.setPacketHandled(true);
    }
}
