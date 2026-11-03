package ru.projectkingdom.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.network.PacketDistributor;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.KingdomNpcNeedsPacket;

/** Bounded status snapshots for loaded, nearby Kingdom residents only. */
public final class KingdomNpcNeedsBroadcast {
    private KingdomNpcNeedsBroadcast(){}
    public static void tick(MinecraftServer server){
        var registry=KingdomNpcData.get(server);
        for(var w:registry.all()){
            for(ServerLevel level:server.getAllLevels()){
                if(!w.dimension.equals(level.dimension().location().toString()))continue;
                var entity=level.getEntity(w.entityId);
                if(!(entity instanceof Villager npc)||!npc.isAlive())continue;
                var status=KingdomNpcWorkDiagnostics.forWorker(level,npc,w);
                var packet=new KingdomNpcNeedsPacket(npc.getUUID(),
                        status.message(),status.warning());
                for(var player:level.players()){
                    if(player.distanceToSqr(npc)>20*20)continue;
                    KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);
                }
            }
        }
    }
}
