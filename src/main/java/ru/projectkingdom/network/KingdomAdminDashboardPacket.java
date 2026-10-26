package ru.projectkingdom.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server-owned, paginated admin screen state; only sent to a verified OP4. */
public record KingdomAdminDashboardPacket(
        UUID session, boolean enabled,
        int cityPage, int cityTotal, List<City> cities,
        int nationPage, int nationTotal, List<Nation> nations,
        int npcPage, int npcTotal, List<Npc> npcs,
        String message, boolean confirming, UUID confirmingTarget, boolean confirmingNation) {
    public record City(UUID id,String name,String mayor,String founder,int plots) {}
    public record Nation(UUID id,String name,String ruler,int cities) {}
    public record Npc(int id,String name,UUID entityId,int age) {}

    public static void encode(KingdomAdminDashboardPacket p,FriendlyByteBuf b){
        b.writeUUID(p.session());b.writeBoolean(p.enabled());
        b.writeVarInt(p.cityPage());b.writeVarInt(p.cityTotal());
        b.writeVarInt(p.cities().size());
        for(var x:p.cities()){
            b.writeUUID(x.id());b.writeUtf(x.name(),64);
            b.writeUtf(x.mayor(),72);b.writeUtf(x.founder(),72);b.writeVarInt(x.plots());
        }
        b.writeVarInt(p.nationPage());b.writeVarInt(p.nationTotal());
        b.writeVarInt(p.nations().size());
        for(var x:p.nations()){
            b.writeUUID(x.id());b.writeUtf(x.name(),64);
            b.writeUtf(x.ruler(),72);b.writeVarInt(x.cities());
        }
        b.writeVarInt(p.npcPage());b.writeVarInt(p.npcTotal());
        b.writeVarInt(p.npcs().size());
        for(var x:p.npcs()){
            b.writeVarInt(x.id());b.writeUtf(x.name(),72);b.writeUUID(x.entityId());b.writeVarInt(x.age());
        }
        b.writeUtf(p.message(),256);b.writeBoolean(p.confirming());
        b.writeUUID(p.confirmingTarget());b.writeBoolean(p.confirmingNation());
    }
    public static KingdomAdminDashboardPacket decode(FriendlyByteBuf b){
        UUID session=b.readUUID();boolean enabled=b.readBoolean();
        int cp=b.readVarInt(),ct=b.readVarInt(),nc=b.readVarInt();
        if(nc<0||nc>24||ct<0||cp<0)throw new IllegalArgumentException("City page");
        var cities=new ArrayList<City>();
        for(int i=0;i<nc;i++)cities.add(new City(b.readUUID(),b.readUtf(64),
            b.readUtf(72),b.readUtf(72),b.readVarInt()));
        int kp=b.readVarInt(),kt=b.readVarInt(),nk=b.readVarInt();
        if(nk<0||nk>24||kt<0||kp<0)throw new IllegalArgumentException("Nation page");
        var nations=new ArrayList<Nation>();
        for(int i=0;i<nk;i++)nations.add(new Nation(b.readUUID(),b.readUtf(64),
            b.readUtf(72),b.readVarInt()));
        int np=b.readVarInt(),nt=b.readVarInt(),nn=b.readVarInt();
        if(nn<0||nn>24||nt<0||np<0)throw new IllegalArgumentException("NPC page");
        var npcs=new ArrayList<Npc>();
        for(int i=0;i<nn;i++)npcs.add(new Npc(b.readVarInt(),b.readUtf(72),b.readUUID(),b.readVarInt()));
        String message=b.readUtf(256);boolean pending=b.readBoolean();
        UUID target=b.readUUID();boolean nation=b.readBoolean();
        return new KingdomAdminDashboardPacket(session,enabled,cp,ct,List.copyOf(cities),
            kp,kt,List.copyOf(nations),np,nt,List.copyOf(npcs),message,pending,target,nation);
    }
    public static void handle(KingdomAdminDashboardPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            ()->()->ru.projectkingdom.npc.client.KingdomAdminDashboardScreen.receive(p)));
        ctx.setPacketHandled(true);
    }
}
