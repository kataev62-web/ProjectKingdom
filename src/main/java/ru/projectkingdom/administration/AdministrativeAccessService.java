package ru.projectkingdom.administration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomContainerAccess;
import ru.projectkingdom.npc.KingdomManagedStorageEntity;

public final class AdministrativeAccessService {
    public enum Status {
        BOUND, NO_SESSION, WRONG_DIMENSION, TOO_FAR, UNLOADED, FOREIGN_TOWN,
        INVALID_TARGET, NO_PERMISSION, CORE_INACTIVE, ALREADY_BOUND, STORAGE_OWNER_MISMATCH, STORAGE_NOT_READY
    }
    private static final Map<UUID,AdministrativeBindingSession> SESSIONS=new ConcurrentHashMap<>();
    private AdministrativeAccessService(){}

    public static String pendingRole(UUID player){
        var session=SESSIONS.get(player);
        return session==null ? "Городское хранилище" : switch(session.role()) {
            case TREASURY_VAULT->"Казна города";
            case BANK_VAULT->"Банковский резерв";
            case WAREHOUSE_STORAGE->"Городской склад";
            case MARKET_STORAGE->"Склад рынка";
            case PRISON_CELL->"Тюремная камера";
        };
    }
    public static String explanation(Status status){
        if(status==null)return "Не удалось определить ошибку. Попробуйте повторить.";
        return switch(status){
            case BOUND->"Подключено! Сундук теперь закреплён за городской службой.";
            case NO_SESSION->"Сначала нажмите «Привязать» в ратуше, банке или другом городском здании.";
            case WRONG_DIMENSION->"Нельзя выбрать сундук в другом измерении. Вернитесь и начните заново.";
            case TOO_FAR->"Слишком далеко. Подойдите к сундуку не дальше 6 блоков и начните заново.";
            case UNLOADED->"Чанк сундука не загружен. Подойдите ближе и повторите.";
            case FOREIGN_TOWN->"Сундук должен находиться на территории именно этого города.";
            case INVALID_TARGET->"Вы выбрали блок без инвентаря. Нужен настоящий сундук, бочка или хранилище Kingdom.";
            case NO_PERMISSION->"Только основатель, мэр или уполномоченный чиновник может привязывать это хранилище.";
            case CORE_INACTIVE->"Эта городская служба больше не активна. Проверьте блок и откройте меню повторно.";
            case ALREADY_BOUND->"Касса уже назначена. Используйте прежний сундук; смена хранилища с деньгами запрещена ради сохранности монет.";
            case STORAGE_OWNER_MISMATCH->"Этот склад Kingdom принадлежит другому игроку. Выберите городской сундук.";
            case STORAGE_NOT_READY->"Склад Kingdom ещё не активирован. Его должен поставить на своей земле владелец и открыть ПКМ. Можно использовать обычный сундук.";
        };
    }
    public static boolean begin(ServerPlayer player,UUID townId,AdministrativeKind kind,AdministrativeBindingRole role){
        if(player==null||townId==null||kind==null||role==null
                ||(role.kind()!=kind && !(kind==AdministrativeKind.TOWN_HALL
                      &&role==AdministrativeBindingRole.TREASURY_VAULT))
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
        if(KingdomBankVaultRooms.get(player.getServer()).at(level,pos)!=null)
            return Status.ALREADY_BOUND;
        KingdomCivicsData civics=KingdomCivicsData.get(player.getServer());var town=civics.townAt(level,pos);
        if(town==null||!town.id().equals(session.townId()))return Status.FOREIGN_TOWN;
        KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());
        if(!AdministrativePermissionService.mayManage(player.getUUID(),data.founder(town.id()),town.mayor(),
                data.roles(town.id(),player.getUUID()),session.kind()))return Status.NO_PERMISSION;
        AdministrativeCoreBinding core=data.core(town.id(),session.kind());
        if(core==null||!core.dimension().equals(session.dimension())||core.packedPos()!=session.corePos())return Status.CORE_INACTIVE;
        if(session.role()!=AdministrativeBindingRole.PRISON_CELL
                && KingdomContainerAccess.resolve(level,pos)==null)return Status.INVALID_TARGET;
        if(level.getBlockEntity(pos) instanceof KingdomManagedStorageEntity managed) {
            if(managed.owner()==null || !managed.readyForTransactions())return Status.STORAGE_NOT_READY;
            if(!managed.owner().equals(player.getUUID()))return Status.STORAGE_OWNER_MISMATCH;
        }
        if(session.role()==AdministrativeBindingRole.PRISON_CELL&&level.getBlockState(pos).isAir())return Status.INVALID_TARGET;
        // Idempotent: binding the same vault again is not an error.
        var previous=data.containerBindings(town.id(),session.role());
        if(previous.stream().anyMatch(c->c.dimension().equals(session.dimension())&&c.packedPos()==pos.asLong()))
            return Status.BOUND;
        if(!session.role().multiple()&&!previous.isEmpty())return Status.ALREADY_BOUND;
        if(!data.bindContainer(town.id(),session.role(),session.dimension(),pos.asLong()))
            return Status.ALREADY_BOUND;
        data.record(town.id(),player.getUUID(),primaryRole(data.roles(town.id(),player.getUUID())),
                "bind."+session.role().name().toLowerCase(),level.getGameTime());
        return Status.BOUND;
    }
    private static MunicipalRole primaryRole(java.util.Set<MunicipalRole> roles){return roles.stream().findFirst().orElse(null);}
}
