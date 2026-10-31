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

/** Two explicit physical clicks, never client-supplied coordinates. */
public final class EnterpriseBuildingSelection {
    private EnterpriseBuildingSelection(){}
    private static final long TTL=2400;
    private record Pending(UUID enterprise,String dimension,BlockPos first,long expires){}
    private static final Map<UUID,Pending> SESSIONS=new ConcurrentHashMap<>();

    public static boolean begin(ServerPlayer player,UUID enterprise){
        if(player==null||player.getServer()==null)return false;
        var data=KingdomEnterpriseData.get(player.getServer());
        if(!data.mayManage(player.getUUID(),enterprise)||data.core(enterprise)==null)return false;
        var core=data.core(enterprise);
        if(!core.active()||!core.dimension().equals(player.serverLevel().dimension().location().toString()))
            return false;
        // Exactly one corner survey must consume each physical click.
        ru.projectkingdom.property.KingdomRentalSelection.cancel(player.getUUID());
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
        ru.projectkingdom.administration.KingdomBankVaultRooms.cancel(player.getUUID());
        ru.projectkingdom.npc.KingdomFarmSelection.cancel(player.getUUID());
        ru.projectkingdom.npc.KingdomForestSelection.cancel(player.getUUID());
        ru.projectkingdom.npc.KingdomStructureCopy.cancel(player.getUUID());
        SESSIONS.put(player.getUUID(),new Pending(enterprise,core.dimension(),null,
                player.serverLevel().getGameTime()+TTL));
        player.sendSystemMessage(Component.literal("[Здание] Пустой рукой Shift+ПКМ по двум противоположным углам здания: нижнему и верхнему. Вся область, включая TFC полки, сундуки, кровати и станки, войдёт в здание."));
        return true;
    }

    public static boolean pending(UUID player){return SESSIONS.containsKey(player);}
    public static void cancel(UUID player){SESSIONS.remove(player);}

    public static boolean select(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
                ||event.getHand()!=InteractionHand.MAIN_HAND||!pending(player.getUUID()))return false;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if(!player.isShiftKeyDown()||!event.getItemStack().isEmpty()){
            player.sendSystemMessage(Component.literal("[Здание] Нужны Shift и пустая основная рука. Первая точка сохраняется."));
            return true;
        }
        var old=SESSIONS.get(player.getUUID());
        if(old==null)return true;
        String dim=player.serverLevel().dimension().location().toString();
        if(player.serverLevel().getGameTime()>old.expires()||!dim.equals(old.dimension())){
            cancel(player.getUUID());
            player.sendSystemMessage(Component.literal("[Здание] Сеанс истёк либо сменилось измерение. Начните заново."));
            return true;
        }
        BlockPos at=event.getPos();
        if(!player.serverLevel().hasChunkAt(at)||at.distSqr(player.blockPosition())>64){
            player.sendSystemMessage(Component.literal("[Здание] Подойдите к углу на расстояние до 8 блоков."));
            return true;
        }
        if(old.first()==null){
            SESSIONS.put(player.getUUID(),new Pending(old.enterprise(),dim,at.immutable(),old.expires()));
            player.sendSystemMessage(Component.literal("[Здание] Первый угол "+at.toShortString()+". Выберите противоположный угол."));
            return true;
        }
        var check=EnterpriseBuildingRegistration.get(player.getServer())
                .registerSelected(player,old.enterprise(),old.first(),at);
        if(!check.success()){
            player.sendSystemMessage(Component.literal("[Здание] "+check.status().message()
                    +". Первая точка сохранена; повторите второй угол."));
            return true;
        }
        cancel(player.getUUID());
        var count=EnterpriseBuildingRegistration.contents(player.serverLevel(),check.room());
        player.sendSystemMessage(Component.literal("[Здание] Привязано "+check.room().dimensions()
                +", кроватей: "+count.beds()+", совместимых хранилищ: "+count.inventories()
                +", блоков TFC: "+count.tfcBlocks()
                +". Предметы и содержимое не перемещались."));
        return true;
    }
}
