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

/**
 * Two-corner selection. Shift + empty hand + right-click soil twice.
 * State is transient, expires after 2 minutes and never bypasses claim checks.
 */
public final class KingdomFarmSelection {
    private KingdomFarmSelection() {}
    private static final long TIMEOUT=2400L;
    private record Pending(UUID farm,String dimension,BlockPos first,long until,boolean pen) {}
    private static final Map<UUID,Pending> PENDING=new ConcurrentHashMap<>();
    public static boolean begin(ServerPlayer player,UUID farm){
        if(!KingdomFarmFieldData.allowed(player,farm))return false;
        PENDING.put(player.getUUID(),new Pending(farm,
            player.serverLevel().dimension().location().toString(),null,
            player.serverLevel().getGameTime()+TIMEOUT,false));
        player.sendSystemMessage(Component.literal("[Ферма] Выделение поля: Shift + ПКМ пустой рукой по ПОЧВЕ первого и второго углов. Максимум 16×16, обе точки на одной высоте."));
        return true;
    }
    /** Same two-corner workflow, but the corners may be ordinary ground. */
    public static boolean beginPen(ServerPlayer player,UUID farm){
        if(!KingdomFarmFieldData.allowed(player,farm))return false;
        PENDING.put(player.getUUID(),new Pending(farm,
            player.serverLevel().dimension().location().toString(),null,
            player.serverLevel().getGameTime()+TIMEOUT,true));
        player.sendSystemMessage(Component.literal("[Животноводство] Shift + ПКМ пустой рукой по двум углам загона. До 16×16, оба угла на одном уровне и на своей земле."));
        return true;
    }
    public static void cancel(UUID user){PENDING.remove(user);}
    public static boolean select(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer p)
            || event.getHand()!=InteractionHand.MAIN_HAND
            || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty())return false;
        Pending pending=PENDING.get(p.getUUID());
        if(pending==null)return false;
        if(p.serverLevel().getGameTime()>pending.until()
            || !p.serverLevel().dimension().location().toString().equals(pending.dimension())
            || !KingdomFarmFieldData.allowed(p,pending.farm())){
            PENDING.remove(p.getUUID());return false;
        }
        BlockPos clicked=event.getPos();
        if(!p.serverLevel().hasChunkAt(clicked))return false;
        // If a crop covers the soil, select its underlying farmland instead.
        var hit=p.serverLevel().getBlockState(clicked);
        String blockId=net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(hit.getBlock()).toString();
        BlockPos soil=blockId.startsWith("tfc:crop/") ? clicked.below() : clicked;
        if(!p.serverLevel().hasChunkAt(soil))return false;
        String soilClass=p.serverLevel().getBlockState(soil).getBlock().getClass().getName();
        if(!pending.pen() && !soilClass.startsWith("net.dries007.tfc.common.blocks.soil.FarmlandBlock")){
            p.sendSystemMessage(Component.literal("[Ферма] Отметь вспаханную почву TFC или растение над ней."));
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return true;
        }
        if(!KingdomTerritories.get(p.getServer()).canUse(p.serverLevel(),soil,p.getUUID())){
            p.sendSystemMessage(Component.literal("[Ферма] Земля должна принадлежать владельцу фермы."));
        }else if(pending.first()==null){
            PENDING.put(p.getUUID(),new Pending(pending.farm(),pending.dimension(),soil.immutable(),pending.until(),pending.pen()));
            p.sendSystemMessage(Component.literal("[Ферма] Первый угол: "+soil.toShortString()+". Отметь второй угол."));
        }else {
            boolean ok=pending.pen()
                ? KingdomRanchPenData.get(p.getServer()).register(p,pending.farm(),pending.first(),soil)
                : KingdomFarmFieldData.get(p.getServer()).create(p,pending.farm(),pending.first(),soil);
            if(ok)PENDING.remove(p.getUUID());
            p.sendSystemMessage(Component.literal(ok
                ? (pending.pen() ? "[Ферма] Загон зарегистрирован. Теперь NPC-животновод может работать с животными внутри него." : "[Ферма] Поле зарегистрировано. Назначь культуру в меню.")
                : "[Ферма] Нельзя добавить: размер до 16×16, одна высота, нет пересечений, до 8 полей, вся земля собственная."));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        return true;
    }
}
