package ru.projectkingdom.enterprise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomTerritories;

/** Loaded-chunk-only binding and access checks for enterprise physical containers. */
public final class EnterpriseAccessService {
    public enum Status { BOUND_AVAILABLE, BOUND_UNLOADED, MISSING, FOREIGN_TERRITORY, INVALID_CONTAINER }
    public record Access(Status status,Container container,BlockPos position) {}
    private static final Map<UUID,EnterpriseBindingSession> SESSIONS=new ConcurrentHashMap<>();
    private EnterpriseAccessService(){}

    public static boolean begin(ServerPlayer player,UUID enterprise,EnterpriseBindingRole role){
        if(player==null||role==null||!KingdomEnterpriseData.get(player.getServer()).mayManage(player.getUUID(),enterprise))return false;
        SESSIONS.put(player.getUUID(),new EnterpriseBindingSession(player.getUUID(),enterprise,role,
                player.level().dimension().location().toString()));return true;
    }
    public static boolean hasSession(UUID player){return SESSIONS.containsKey(player);}
    public static void cancel(UUID player){SESSIONS.remove(player);}

    public static Status complete(ServerPlayer player,ServerLevel level,BlockPos pos){
        EnterpriseBindingSession session=SESSIONS.remove(player.getUUID());
        if(session==null||!session.dimension().equals(level.dimension().location().toString())
                ||pos.distSqr(player.blockPosition())>36)return Status.MISSING;
        var data=KingdomEnterpriseData.get(player.getServer());var firm=data.find(session.enterpriseId());
        if(firm==null||!firm.owner().equals(player.getUUID()))return Status.FOREIGN_TERRITORY;
        if(!level.hasChunkAt(pos))return Status.BOUND_UNLOADED;
        if(!KingdomTerritories.get(player.getServer()).canUse(level,pos,firm.owner()))return Status.FOREIGN_TERRITORY;
        if(!(level.getBlockEntity(pos) instanceof Container))return Status.INVALID_CONTAINER;
        if(!data.bindStorage(player.getUUID(),session.enterpriseId(),session.role(),
                level.dimension().location().toString(),pos))return Status.INVALID_CONTAINER;
        return Status.BOUND_AVAILABLE;
    }

    public static Access snapshot(ServerLevel level,KingdomEnterpriseData data,UUID enterprise,EnterpriseBindingRole role){
        var binding=data.storageBinding(enterprise,role);
        if(binding==null)return new Access(Status.MISSING,null,null);
        if(!binding.dimension().equals(level.dimension().location().toString()))return new Access(Status.BOUND_UNLOADED,null,binding.pos());
        if(!level.hasChunkAt(binding.pos()))return new Access(Status.BOUND_UNLOADED,null,binding.pos());
        var firm=data.find(enterprise);
        if(firm==null||!KingdomTerritories.get(level.getServer()).canUse(level,binding.pos(),firm.owner()))
            return new Access(Status.FOREIGN_TERRITORY,null,binding.pos());
        if(!(level.getBlockEntity(binding.pos()) instanceof Container container))
            return new Access(Status.INVALID_CONTAINER,null,binding.pos());
        return new Access(Status.BOUND_AVAILABLE,container,binding.pos());
    }
}
