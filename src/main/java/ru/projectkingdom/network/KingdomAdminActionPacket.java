package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Client only expresses intent; never chooses permissions or mutation coordinates. */
public record KingdomAdminActionPacket(UUID session,int action,UUID target,int npcId,
                                       String name,int page){
    public static final int TOGGLE=0,CREATE_CITY=1,CREATE_NATION=2,
        APPOINT_MAYOR=3,APPOINT_FOUNDER=4,APPOINT_RULER=5,
        CLAIM_CURRENT_CHUNK=6,PREPARE_CITY_DELETE=7,PREPARE_NATION_DELETE=8,
        CONFIRM_DELETE=9,CANCEL_DELETE=10,REFRESH=11,
        CITY_PAGE=12,NATION_PAGE=13,NPC_PAGE=14;

    public static void encode(KingdomAdminActionPacket p,FriendlyByteBuf b){
        b.writeUUID(p.session());b.writeVarInt(p.action());b.writeUUID(p.target());
        b.writeVarInt(p.npcId());b.writeUtf(p.name(),48);b.writeVarInt(p.page());
    }
    public static KingdomAdminActionPacket decode(FriendlyByteBuf b){
        return new KingdomAdminActionPacket(b.readUUID(),b.readVarInt(),b.readUUID(),
            b.readVarInt(),b.readUtf(48),b.readVarInt());
    }
    public static void handle(KingdomAdminActionPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->{
            var player=ctx.getSender();
            if(player!=null)ru.projectkingdom.npc.KingdomAdminDashboardService.act(player,p);
        });
        ctx.setPacketHandled(true);
    }
}
