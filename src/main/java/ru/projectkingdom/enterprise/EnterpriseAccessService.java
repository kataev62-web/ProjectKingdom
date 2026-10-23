package ru.projectkingdom.enterprise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomContainerAccess;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomTerritories;
import ru.projectkingdom.npc.KingdomManagedStorageEntity;

/** Container binding with precise failure diagnoses, always server-authorized. */
public final class EnterpriseAccessService {
    public enum Status {
        BOUND_AVAILABLE, BOUND_UNLOADED, MISSING, FOREIGN_TERRITORY, INVALID_CONTAINER,
        ALREADY_ASSIGNED, TOO_FAR, NO_SESSION, WRONG_DIMENSION, NO_PERMISSION, STORAGE_OWNER_MISMATCH
    }
    public record Access(Status status,Container container,BlockPos position) {}
    private static final Map<UUID,EnterpriseBindingSession> SESSIONS=new ConcurrentHashMap<>();
    private EnterpriseAccessService() {}

    public static String label(Status status) {
        if(status==null)return "Неизвестное состояние";
        return switch(status) {
            case BOUND_AVAILABLE->"Подключено, работает";
            case BOUND_UNLOADED->"Недоступно: чанк не загружен";
            case MISSING->"Не подключено";
            case FOREIGN_TERRITORY->"Нет прав на участок";
            case INVALID_CONTAINER->"Это не подходящий сундук или склад";
            case ALREADY_ASSIGNED->"Сундук уже занят другой привязкой";
            case TOO_FAR->"Слишком далеко от сундука";
            case NO_SESSION->"Сначала нажмите кнопку привязки";
            case WRONG_DIMENSION->"Нельзя привязать в другом измерении";
            case NO_PERMISSION->"Нет прав владельца предприятия";
            case STORAGE_OWNER_MISMATCH->"Склад принадлежит другому игроку";
        };
    }

    public static String explanation(Status status) {
        if(status==null)return "Повторите действие.";
        return switch(status) {
            case BOUND_AVAILABLE->"Готово! Хранилище подключено. Повторно открыв предприятие, вы увидите статус «Подключено».";
            case BOUND_UNLOADED->"Подойдите к хранилищу, чтобы загрузить его чанк, и откройте меню повторно.";
            case MISSING->"Нажмите «Касса», «Сырьё» или «Продукция», затем ПКМ по нужному сундуку.";
            case FOREIGN_TERRITORY->"Сундук должен находиться на вашем участке. Проверьте право собственности на землю.";
            case INVALID_CONTAINER->"Нажмите ПКМ по самому сундуку, бочке или блоку склада Kingdom. У обычного блока нет инвентаря. Для несовместимого хранилища попробуйте обычный сундук.";
            case ALREADY_ASSIGNED->"Нужен отдельный сундук для каждой роли: касса, сырьё и готовая продукция. Другому предприятию этот сундук тоже недоступен.";
            case TOO_FAR->"Подойдите к сундуку на расстояние не дальше 6 блоков и повторите привязку.";
            case NO_SESSION->"Сначала откройте своё предприятие и нажмите кнопку нужной роли на вкладке «Склады».";
            case WRONG_DIMENSION->"Вернитесь в измерение, где начинали привязку, и начните её заново.";
            case NO_PERMISSION->"Привязку выполняет только владелец предприятия.";
            case STORAGE_OWNER_MISMATCH->"Выберите свой склад Kingdom или используйте обычный сундук на своём участке.";
        };
    }
    public static String display(Status status) {return label(status);}
    public static boolean begin(ServerPlayer player,UUID enterprise,EnterpriseBindingRole role){
        if(player==null||role==null||!KingdomEnterpriseData.get(player.getServer()).mayManage(player.getUUID(),enterprise))return false;
        SESSIONS.put(player.getUUID(),new EnterpriseBindingSession(player.getUUID(),enterprise,role,
                player.level().dimension().location().toString()));return true;
    }
    public static boolean hasSession(UUID player){return SESSIONS.containsKey(player);}
    public static void cancel(UUID player){SESSIONS.remove(player);}
    public static String pendingRole(UUID player) {
        var session=SESSIONS.get(player);
        return session==null ? "Склад" : session.role().title();
    }

    private static boolean mayUse(ServerLevel level,BlockPos pos,UUID owner) {
        if(KingdomTerritories.get(level.getServer()).canUse(level,pos,owner))return true;
        var city=KingdomCivicsData.get(level.getServer());
        var plot=city.plot(level,pos);
        return plot!=null && owner!=null
                && owner.equals(city.plotOwner(level.dimension().location().toString(),pos.getX()>>4,pos.getZ()>>4));
    }
    private static Status checkContainer(ServerLevel level,BlockPos pos,UUID owner) {
        if(level.getBlockEntity(pos) instanceof KingdomManagedStorageEntity managed
                && !owner.equals(managed.owner()))return Status.STORAGE_OWNER_MISMATCH;
        return KingdomContainerAccess.resolve(level,pos)==null ? Status.INVALID_CONTAINER : Status.BOUND_AVAILABLE;
    }
    private static boolean assignedElsewhere(KingdomEnterpriseData data,UUID enterprise,EnterpriseBindingRole role,
                                               ServerLevel level,BlockPos pos){
        String dim=level.dimension().location().toString();
        for(var other:data.all())for(var type:EnterpriseBindingRole.values()){
            var bound=data.storageBinding(other.id(),type);
            if(bound!=null && dim.equals(bound.dimension()) && pos.equals(bound.pos())
                    && (!enterprise.equals(other.id()) || role!=type))return true;
        }
        return false;
    }

    public static Status complete(ServerPlayer player,ServerLevel level,BlockPos pos){
        EnterpriseBindingSession session=SESSIONS.remove(player.getUUID());
        if(session==null)return Status.NO_SESSION;
        if(!session.dimension().equals(level.dimension().location().toString()))return Status.WRONG_DIMENSION;
        if(pos.distSqr(player.blockPosition())>36)return Status.TOO_FAR;
        var data=KingdomEnterpriseData.get(player.getServer());var firm=data.find(session.enterpriseId());
        if(firm==null||!firm.owner().equals(player.getUUID()))return Status.NO_PERMISSION;
        if(!level.hasChunkAt(pos))return Status.BOUND_UNLOADED;
        if(!mayUse(level,pos,firm.owner()))return Status.FOREIGN_TERRITORY;
        Status container=checkContainer(level,pos,firm.owner());
        if(container!=Status.BOUND_AVAILABLE)return container;
        if(assignedElsewhere(data,session.enterpriseId(),session.role(),level,pos))return Status.ALREADY_ASSIGNED;
        if(!data.bindStorage(player.getUUID(),session.enterpriseId(),session.role(),
                level.dimension().location().toString(),pos))return Status.ALREADY_ASSIGNED;
        return Status.BOUND_AVAILABLE;
    }

    public static Access snapshot(ServerLevel level,KingdomEnterpriseData data,UUID enterprise,EnterpriseBindingRole role){
        var binding=data.storageBinding(enterprise,role);
        if(binding==null)return new Access(Status.MISSING,null,null);
        if(!binding.dimension().equals(level.dimension().location().toString()))return new Access(Status.WRONG_DIMENSION,null,binding.pos());
        if(!level.hasChunkAt(binding.pos()))return new Access(Status.BOUND_UNLOADED,null,binding.pos());
        var firm=data.find(enterprise);
        if(firm==null||!mayUse(level,binding.pos(),firm.owner()))
            return new Access(Status.FOREIGN_TERRITORY,null,binding.pos());
        Status check=checkContainer(level,binding.pos(),firm.owner());
        if(check!=Status.BOUND_AVAILABLE)return new Access(check,null,binding.pos());
        return new Access(Status.BOUND_AVAILABLE,KingdomContainerAccess.resolve(level,binding.pos()),binding.pos());
    }
}
