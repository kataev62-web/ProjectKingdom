package ru.projectkingdom.enterprise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import ru.projectkingdom.npc.KingdomEnterpriseData;

/**
 * Three-step physical selection: two left-clicks define the horizontal floor
 * rectangle; one right-click defines its upper height. Coordinates always come
 * from server-side interaction events, never from client packets.
 */
public final class EnterpriseBuildingSelection {
    private EnterpriseBuildingSelection(){}
    private static final long TTL=2400;
    private record Pending(UUID enterprise,String dimension,BlockPos firstFloor,
                           BlockPos oppositeFloor,long expires){}
    private static final Map<UUID,Pending> SESSIONS=new ConcurrentHashMap<>();

    public static boolean begin(ServerPlayer player,UUID enterprise){
        if(player==null||player.getServer()==null)return false;
        var data=KingdomEnterpriseData.get(player.getServer());
        if(!data.mayManage(player.getUUID(),enterprise)||data.core(enterprise)==null)return false;
        var core=data.core(enterprise);
        if(!core.active()||!core.dimension().equals(player.serverLevel().dimension().location().toString()))
            return false;
        ru.projectkingdom.property.KingdomRentalSelection.cancel(player.getUUID());
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
        ru.projectkingdom.administration.KingdomBankVaultRooms.cancel(player.getUUID());
        ru.projectkingdom.npc.KingdomFarmSelection.cancel(player.getUUID());
        ru.projectkingdom.npc.KingdomForestSelection.cancel(player.getUUID());
        ru.projectkingdom.npc.KingdomStructureCopy.cancel(player.getUUID());
        SESSIONS.put(player.getUUID(),new Pending(enterprise,core.dimension(),null,null,
                player.serverLevel().getGameTime()+TTL));
        ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                ru.projectkingdom.network.KingdomVolumeSelectionPacket.ENTERPRISE,null,true);
        player.sendSystemMessage(Component.literal(
                "[Здание] Шаг 1/2: пустой рукой Shift+ЛКМ выберите два противоположных угла пола."));
        return true;
    }

    public static boolean pending(UUID player){return SESSIONS.containsKey(player);}
    public static void cancel(UUID player){SESSIONS.remove(player);}

    private static Pending validSession(ServerPlayer player){
        Pending old=SESSIONS.get(player.getUUID());
        if(old==null)return null;
        String dim=player.serverLevel().dimension().location().toString();
        if(player.serverLevel().getGameTime()>old.expires()||!dim.equals(old.dimension())){
            cancel(player.getUUID());
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                    ru.projectkingdom.network.KingdomVolumeSelectionPacket.ENTERPRISE,null,false);
            player.sendSystemMessage(Component.literal("[Здание] Сеанс истёк либо сменилось измерение. Начните заново."));
            return null;
        }
        return old;
    }

    /** First and second LMB clicks select only the horizontal floor rectangle. */
    public static boolean selectHorizontal(PlayerInteractEvent.LeftClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!pending(player.getUUID()))return false;
        event.setCanceled(true);
        if(!player.isShiftKeyDown()||!event.getItemStack().isEmpty()){
            player.sendSystemMessage(Component.literal("[Здание] Для выделения нужны Shift и пустая основная рука."));
            return true;
        }
        Pending old=validSession(player);
        if(old==null)return true;
        BlockPos at=event.getPos();
        if(!player.serverLevel().hasChunkAt(at)||at.distSqr(player.blockPosition())>64){
            player.sendSystemMessage(Component.literal("[Здание] Подойдите к точке на расстояние до 8 блоков."));
            return true;
        }
        if(old.firstFloor()==null){
            SESSIONS.put(player.getUUID(),new Pending(old.enterprise(),old.dimension(),
                    at.immutable(),null,old.expires()));
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                    ru.projectkingdom.network.KingdomVolumeSelectionPacket.ENTERPRISE,at,true);
            player.sendSystemMessage(Component.literal("[Здание] Первый угол пола отмечен. Выберите противоположный угол пола ЛКМ на том же уровне."));
            return true;
        }
        if(old.oppositeFloor()==null){
            if(at.getY()!=old.firstFloor().getY()){
                player.sendSystemMessage(Component.literal("[Здание] Второй угол пола должен находиться на том же уровне по высоте."));
                return true;
            }
            if(at.getX()==old.firstFloor().getX()||at.getZ()==old.firstFloor().getZ()){
                player.sendSystemMessage(Component.literal("[Здание] Выберите именно противоположный угол прямоугольника, а не ту же сторону."));
                return true;
            }
            SESSIONS.put(player.getUUID(),new Pending(old.enterprise(),old.dimension(),
                    old.firstFloor(),at.immutable(),old.expires()));
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                    ru.projectkingdom.network.KingdomVolumeSelectionPacket.ENTERPRISE,at,true);
            player.sendSystemMessage(Component.literal("[Здание] Горизонт пола выделен. Шаг 2/2: Shift+ПКМ выберите верхнюю точку по высоте."));
            return true;
        }
        player.sendSystemMessage(Component.literal("[Здание] Горизонт уже выбран. Теперь задайте высоту ПКМ."));
        return true;
    }

    /** One RMB click supplies the vertical endpoint and commits the 3D room. */
    public static boolean selectVertical(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
                ||event.getHand()!=InteractionHand.MAIN_HAND||!pending(player.getUUID()))return false;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if(!player.isShiftKeyDown()||!event.getItemStack().isEmpty()){
            player.sendSystemMessage(Component.literal("[Здание] Для выделения нужны Shift и пустая основная рука."));
            return true;
        }
        Pending old=validSession(player);
        if(old==null)return true;
        if(old.firstFloor()==null||old.oppositeFloor()==null){
            player.sendSystemMessage(Component.literal("[Здание] Сначала выделите горизонт пола двумя нажатиями ЛКМ."));
            return true;
        }
        BlockPos at=event.getPos();
        if(!player.serverLevel().hasChunkAt(at)||at.distSqr(player.blockPosition())>64){
            player.sendSystemMessage(Component.literal("[Здание] Подойдите к верхней точке на расстояние до 8 блоков."));
            return true;
        }
        int floorY=old.firstFloor().getY();
        if(at.getY()<floorY+2){
            player.sendSystemMessage(Component.literal("[Здание] Высота помещения должна быть не менее трёх блоков вместе с полом."));
            return true;
        }
        BlockPos first=new BlockPos(Math.min(old.firstFloor().getX(),old.oppositeFloor().getX()),
                floorY,Math.min(old.firstFloor().getZ(),old.oppositeFloor().getZ()));
        BlockPos second=new BlockPos(Math.max(old.firstFloor().getX(),old.oppositeFloor().getX()),
                at.getY(),Math.max(old.firstFloor().getZ(),old.oppositeFloor().getZ()));
        var check=EnterpriseBuildingRegistration.get(player.getServer())
                .registerSelected(player,old.enterprise(),first,second);
        if(!check.success()){
            player.sendSystemMessage(Component.literal("[Здание] "+check.status().message()
                    +". Горизонт сохранён; повторите выбор высоты ПКМ."));
            return true;
        }
        cancel(player.getUUID());
        ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                ru.projectkingdom.network.KingdomVolumeSelectionPacket.ENTERPRISE,null,false);
        var count=EnterpriseBuildingRegistration.contents(player.serverLevel(),check.room());
        player.sendSystemMessage(Component.literal("[Здание] Привязано "+check.room().dimensions()
                +", кроватей: "+count.beds()+", совместимых хранилищ: "+count.inventories()
                +", блоков TFC: "+count.tfcBlocks()+". Предметы и содержимое не перемещались."));
        return true;
    }
}
