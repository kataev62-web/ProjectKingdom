package ru.projectkingdom.administration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import ru.projectkingdom.npc.KingdomCivicsData;

public final class AdministrativeAccessService {
    public enum Status {BOUND,NO_SESSION,WRONG_DIMENSION,TOO_FAR,UNLOADED,FOREIGN_TOWN,INVALID_TARGET,NO_PERMISSION,CORE_INACTIVE}
    private static final Map<UUID,AdministrativeBindingSession> SESSIONS=new ConcurrentHashMap<>();
    private AdministrativeAccessService(){}
    public static boolean begin(ServerPlayer player,UUID townId,AdministrativeKind kind,AdministrativeBindingRole role){
        if(player==null||townId==null||kind==null||role==null||role.kind()!=kind
                ||!(player.containerMenu instanceof AdministrativeCoreMenu menu)
                ||!townId.equals(menu.townId())||kind!=menu.kind()||!menu.manager()||!menu.stillValid(player))return false;
        SESSIONS.put(player.getUUID(),new AdministrativeBindingSession(player.getUUID(),townId,kind,role,
                player.level().dimension().location().toString(),menu.corePos().asLong()));return true;
    }
    public static boolean hasSession(UUID playerId){return SESSIONS.containsKey(playerId);}
    public static void cancel(UUID playerId){SESSIONS.remove(playerId);}
    public static Status complete(ServerPlayer player,ServerLevel level,BlockPos pos){
        AdministrativeBindingSession session=SESSIONS.remove(player.getUUID());
        if(session==null)return Status.NO_SESSION;
        if(!session.dimension().equals(level.dimension().location().toString()))return Status.WRONG_DIMENSION;
        if(pos.distSqr(player.blockPosition())>36)return Status.TOO_FAR;
        if(!level.hasChunkAt(pos))return Status.UNLOADED;
        KingdomCivicsData civics=KingdomCivicsData.get(player.getServer());var town=civics.townAt(level,pos);
        if(town==null||!town.id().equals(session.townId()))return Status.FOREIGN_TOWN;
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());
        if(!AdministrativePermissionService.mayManage(player.getUUID(),data.founder(town.id()),town.mayor(),
                data.roles(town.id(),player.getUUID()),session.kind()))return Status.NO_PERMISSION;
        AdministrativeCoreBinding core=data.core(town.id(),session.kind());
        if(core==null||!core.dimension().equals(session.dimension())||core.packedPos()!=session.corePos())return Status.CORE_INACTIVE;
        if(session.role()!=AdministrativeBindingRole.PRISON_CELL&&!(level.getBlockEntity(pos) instanceof Container))return Status.INVALID_TARGET;
        if(session.role()==AdministrativeBindingRole.PRISON_CELL&&level.getBlockState(pos).isAir())return Status.INVALID_TARGET;
        if(!data.bindContainer(town.id(),session.role(),session.dimension(),pos.asLong()))return Status.INVALID_TARGET;
        data.record(town.id(),player.getUUID(),primaryRole(data.roles(town.id(),player.getUUID())),"bind."+session.role().name().toLowerCase(),level.getGameTime());
        return Status.BOUND;
    }
    private static MunicipalRole primaryRole(java.util.Set<MunicipalRole> roles){return roles.stream().findFirst().orElse(null);}
}
