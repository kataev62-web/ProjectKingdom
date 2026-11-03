package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** Small S2C status snapshot, sourced only from an actual loaded NPC on server. */
public record KingdomNpcNeedsPacket(UUID npc,String text,boolean warning) {
    public KingdomNpcNeedsPacket {
        if(npc==null || text==null || text.isBlank() || text.length()>112)
            throw new IllegalArgumentException("invalid NPC status");
    }
    public static void encode(KingdomNpcNeedsPacket p,FriendlyByteBuf b){
        b.writeUUID(p.npc());b.writeUtf(p.text(),112);b.writeBoolean(p.warning());
    }
    public static KingdomNpcNeedsPacket decode(FriendlyByteBuf b){
        return new KingdomNpcNeedsPacket(b.readUUID(),b.readUtf(112),b.readBoolean());
    }
    public static void handle(KingdomNpcNeedsPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        if(ctx.getDirection()==NetworkDirection.PLAY_TO_CLIENT)
            ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->ru.projectkingdom.npc.client.KingdomNpcNeedsOverlay.accept(p)));
        ctx.setPacketHandled(true);
    }
}
