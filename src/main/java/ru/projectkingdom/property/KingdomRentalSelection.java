package ru.projectkingdom.property;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import ru.projectkingdom.npc.KingdomCivicsData;

/**
 * Selection sessions are transient, per-player and require two physical
 * block interactions. No client-provided coordinates or title assertions.
 */
public final class KingdomRentalSelection {
    private KingdomRentalSelection(){}
    private static final long TTL_TICKS=2400;
    private enum Mode { BUILDING, ROOM }
    private record Pending(Mode mode,String dimension,UUID building,
                           BlockPos first,long expires) {}
    private static final Map<UUID,Pending> PENDING=new ConcurrentHashMap<>();

    public static boolean beginBuilding(ServerPlayer player){
        if(player==null || player.getServer()==null)return false;
        PENDING.put(player.getUUID(),new Pending(Mode.BUILDING,
                player.serverLevel().dimension().location().toString(),null,
                null,player.serverLevel().getGameTime()+TTL_TICKS));
        message(player,"Выделение здания: Shift+ПКМ пустой рукой по двум противоположным углам здания, включая нижний и верхний этаж. Земля должна принадлежать вам.");
        return true;
    }
    public static boolean beginRoom(ServerPlayer player){
        if(player==null)return false;
        var data=KingdomRentalsData.get(player.getServer());
        var b=data.atBuilding(player.serverLevel().dimension().location().toString(),
                player.blockPosition());
        if(b==null||!b.owner().equals(player.getUUID()))return false;
        if(!KingdomRentalsData.ownsFullFootprint(KingdomCivicsData.get(player.getServer()),
                player.getUUID(),b.dimension(),b.bounds()))return false;
        PENDING.put(player.getUUID(),new Pending(Mode.ROOM,b.dimension(),b.id(),null,
                player.serverLevel().getGameTime()+TTL_TICKS));
        message(player,"Выделение комнаты: Shift+ПКМ пустой рукой по двум противоположным углам. Размеченная комната должна находиться внутри вашего здания.");
        return true;
    }
    public static void cancel(UUID user){PENDING.remove(user);}
    public static boolean handle(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
                ||event.getHand()!=InteractionHand.MAIN_HAND
                ||!player.isShiftKeyDown()||!player.getMainHandItem().isEmpty())return false;
        var waiting=PENDING.get(player.getUUID());
        if(waiting==null)return false;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        String dim=player.serverLevel().dimension().location().toString();
        if(player.serverLevel().getGameTime()>waiting.expires()
                ||!dim.equals(waiting.dimension())){
            PENDING.remove(player.getUUID());
            message(player,"Время выделения истекло. Начните заново.");
            return true;
        }
        BlockPos corner=event.getPos();
        if(!player.serverLevel().hasChunkAt(corner))return true;
        if(waiting.first()==null){
            PENDING.put(player.getUUID(),new Pending(waiting.mode(),dim,waiting.building(),
                    corner.immutable(),waiting.expires()));
            message(player,"Первый угол "+corner.toShortString()+". Отметьте второй.");
            return true;
        }
        KingdomRentalsData.Bounds box;
        try{box=KingdomRentalsData.Bounds.between(waiting.first(),corner);}
        catch(IllegalArgumentException bad){
            message(player,"Слишком большая область. Здание или помещение: максимум 64×48×64, до 32768 блоков.");
            return true;
        }
        var rentals=KingdomRentalsData.get(player.getServer());
        var deeds=KingdomCivicsData.get(player.getServer());
        boolean ok;
        if(waiting.mode()==Mode.BUILDING){
            var b=rentals.registerBuilding(deeds,player.getUUID(),dim,
                    "Здание "+(rentals.buildings().size()+1),box);
            ok=b!=null;
            if(ok)message(player,"Здание зарегистрировано! Теперь зайдите внутрь и откройте Kingdom → Недвижимость → Разметить комнату.");
        }else{
            var room=rentals.createRoom(deeds,player.getUUID(),waiting.building(),
                    "Помещение "+(rentals.rooms().size()+1),KingdomRentalsData.Purpose.HOME,box);
            ok=room!=null;
            if(ok){
                rentals.select(player,room.id());
                message(player,"Комната зарегистрирована, объявление ещё закрыто. Откройте меню «Недвижимость» и установите цену аренды.");
            }
        }
        if(ok)PENDING.remove(player.getUUID());
        else message(player,"Область отклонена: проверьте титул на землю, отсутствие аренды чанка, пересечения с чужими комнатами и границы здания.");
        return true;
    }
    private static void message(ServerPlayer p,String text){
        p.sendSystemMessage(Component.literal("[Kingdom Недвижимость] "+text));
    }
}
