package ru.projectkingdom.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Explicit two-click owned forest survey, never grants land or loads chunks. */
public final class KingdomForestSelection {
    private KingdomForestSelection(){}
    private record Session(UUID enterprise,String dimension,BlockPos first,long expiry){}
    private static final Map<UUID,Session> ACTIVE=new ConcurrentHashMap<>();
    public static boolean begin(ServerPlayer actor,UUID lumber){
        if(!KingdomForestData.allowed(actor,lumber))return false;
        ACTIVE.put(actor.getUUID(),new Session(lumber,
            actor.serverLevel().dimension().location().toString(),null,
            actor.serverLevel().getGameTime()+2400L));
        actor.sendSystemMessage(Component.literal("[Лес] Shift + ПКМ пустой рукой по двум углам СВОЕГО лесного участка, до 16×16, одна высота."));
        return true;
    }
    public static boolean select(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player)
            ||event.getHand()!=InteractionHand.MAIN_HAND
            ||!player.isShiftKeyDown()||!player.getMainHandItem().isEmpty())return false;
        var selected=ACTIVE.get(player.getUUID());
        if(selected==null)return false;
        var level=player.serverLevel();
        if(level.getGameTime()>selected.expiry()
            ||!level.dimension().location().toString().equals(selected.dimension())
            ||!KingdomForestData.allowed(player,selected.enterprise())){
            ACTIVE.remove(player.getUUID());return false;
        }
        BlockPos corner=event.getPos();
        if(!level.hasChunkAt(corner))return false;
        if(!KingdomTerritories.get(player.getServer()).canUse(level,corner,player.getUUID())){
            player.sendSystemMessage(Component.literal("[Лес] Нельзя размечать чужую землю."));
        }else if(selected.first()==null){
            ACTIVE.put(player.getUUID(),new Session(selected.enterprise(),
                selected.dimension(),corner.immutable(),selected.expiry()));
            player.sendSystemMessage(Component.literal("[Лес] Первый угол записан: "+corner.toShortString()+". Укажи второй."));
        }else{
            boolean ok=KingdomForestData.get(player.getServer()).add(
                player,selected.enterprise(),selected.first(),corner);
            if(ok)ACTIVE.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(ok?
                "[Лес] Участок создан. Выбери породу и нормы запаса в меню лесозаготовки.":
                "[Лес] Участок не создан: нужны своя земля, сторона до 16 блоков, одна высота и отсутствие пересечений."));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
        return true;
    }
}
