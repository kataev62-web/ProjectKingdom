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
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Two horizontal corners on the SURFACE. Nothing is mined by selection.
 * Uses the existing empty-hand Shift+RMB survey convention.
 */
public final class KingdomMineSelection {
    private KingdomMineSelection() {}
    private record Pending(UUID mine,String dimension,BlockPos first,long expiry){}
    private static final Map<UUID,Pending> ACTIVE=new ConcurrentHashMap<>();

    public static void cancel(UUID user){ACTIVE.remove(user);}
    public static boolean begin(ServerPlayer player,UUID mine){
        var enterprises=KingdomEnterpriseData.get(player.getServer());
        var company=enterprises.find(mine);
        var binding=enterprises.core(mine);
        if(company==null||binding==null||!binding.active()
                ||enterprises.kind(mine)!=EnterpriseKind.MINE
                ||!company.owner().equals(player.getUUID())
                ||!binding.dimension().equals(player.level().dimension().location().toString())
                ||KingdomMineSettings.get(player.getServer()).started(mine))return false;
        // Only one surveying action owns the next two clicks.
        KingdomFarmSelection.cancel(player.getUUID());
        KingdomForestSelection.cancel(player.getUUID());
        ru.projectkingdom.enterprise.EnterpriseBuildingSelection.cancel(player.getUUID());
        ru.projectkingdom.property.KingdomRentalSelection.cancel(player.getUUID());
        ru.projectkingdom.administration.KingdomBankVaultRooms.cancel(player.getUUID());
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
        KingdomStructureCopy.cancel(player.getUUID());
        ACTIVE.put(player.getUUID(),new Pending(mine,binding.dimension(),null,
            player.serverLevel().getGameTime()+2400));
        ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,null,true);
        player.sendSystemMessage(Component.literal(
            "[Шахта] Отметь 2 противоположных угла прямоугольника: "
            +"Shift+ЛКМ или Shift+ПКМ пустой рукой. Размер 5×5 ... 12×12 блоков. "
            +"Ствол должен примыкать к ядру с любой стороны. "
            +"Высота берётся по ядру; после первой точки выделение не сбрасывается."));
        return true;
    }

    public static boolean select(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
            ||event.getHand()!=InteractionHand.MAIN_HAND
            ||!ACTIVE.containsKey(player.getUUID()))return false;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        return selectCorner(player,event.getPos());
    }

    /** Supports left-click marking as well as right-click, without breaking the clicked block. */
    public static boolean select(PlayerInteractEvent.LeftClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
            ||event.getHand()!=InteractionHand.MAIN_HAND
            ||!ACTIVE.containsKey(player.getUUID()))return false;
        event.setCanceled(true);
        return selectCorner(player,event.getPos());
    }

    private static boolean selectCorner(ServerPlayer player,BlockPos at) {
        var p=ACTIVE.get(player.getUUID());
        if(p==null)return true;
        var level=player.serverLevel();
        if(level.getGameTime()>p.expiry()
            ||!p.dimension().equals(level.dimension().location().toString())) {
            cancel(player.getUUID());
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,null,false);
            return true;
        }
        if(!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()){
            player.sendSystemMessage(Component.literal("[Шахта] Используй Shift+ЛКМ или Shift+ПКМ пустой рукой. Первая точка сохранена."));
            return true;
        }
        if(!level.hasChunkAt(at)||at.distSqr(player.blockPosition())>64){
            player.sendSystemMessage(Component.literal("[Шахта] Подойди к углу в пределах 8 блоков."));
            return true;
        }
        var firms=KingdomEnterpriseData.get(player.getServer());
        var company=firms.find(p.mine());
        if(company==null||!company.owner().equals(player.getUUID())
            ||firms.kind(p.mine())!=EnterpriseKind.MINE
            ||KingdomMineSettings.get(player.getServer()).started(p.mine())){
            cancel(player.getUUID());return true;
        }
        if(p.first()==null){
            ACTIVE.put(player.getUUID(),new Pending(p.mine(),p.dimension(),
                at.immutable(),p.expiry()));
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
                ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,at,true);
            player.sendSystemMessage(Component.literal("[Шахта] Первый угол "
                +at.toShortString()+". Отметь второй угол по диагонали; можно на другой высоте."));
            return true;
        }
        BlockPos first=p.first();
        int minX=Math.min(first.getX(),at.getX()),maxX=Math.max(first.getX(),at.getX());
        int minZ=Math.min(first.getZ(),at.getZ()),maxZ=Math.max(first.getZ(),at.getZ());
        int width=maxX-minX+1,length=maxZ-minZ+1;
        var core=firms.core(p.mine());
        if(core==null||!core.active()||!core.dimension().equals(p.dimension())){
            player.sendSystemMessage(Component.literal("[Шахта] Ядро шахты неактивно. Первая точка сохранена."));
            return true;
        }
        if(width<5||length<5||width>12||length>12){
            player.sendSystemMessage(Component.literal("[Шахта] Сейчас выделено "+width+"×"+length
                +". Нужно 5×5 ... 12×12. Первая точка сохранена; повтори второй угол."));
            return true;
        }
        // Only the mine-core elevation determines the planned shaft floor.
        // Surface clicked blocks may have different heights in TFC terrain.
        int surfaceY=BlockPos.of(core.packedPos()).getY();
        var territory=KingdomTerritories.get(player.getServer());
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            for(int y=surfaceY-10;y<=surfaceY;y++) {
                BlockPos cell=new BlockPos(x,y,z);
                if(!level.hasChunkAt(cell)
                    ||!territory.canUse(level,cell,company.owner())){
                    player.sendSystemMessage(Component.literal("[Шахта] Нет доступа или не загружена клетка "+cell.toShortString()
                        +". Первая точка сохранена; исправь участок и повтори второй угол."));
                    return true;
                }
            }
        }
        var area=new KingdomMineSettings.Shaft(minX,maxX,minZ,maxZ,
            surfaceY,p.dimension());
        if(!KingdomMineCentralShaftPlan.valid(area,BlockPos.of(core.packedPos()))) {
            player.sendSystemMessage(Component.literal(
                "[Шахта] Ствол должен примыкать к ядру с СЕВЕРА, ЮГА, ЗАПАДА "
                +"или ВОСТОКА: между ядром и прямоугольником должна быть "
                +"общая граница без промежутка. Уровень Y="+surfaceY
                +". Первая точка сохранена."));
            return true;
        }
        if(!KingdomMineSettings.get(player.getServer()).setShaft(p.mine(),area)){
            player.sendSystemMessage(Component.literal("[Шахта] Изменение ствола запрещено после начала проходки."));
            return true;
        }
        cancel(player.getUUID());
        ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,null,false);
        player.sendSystemMessage(Component.literal("[Шахта] Центральный ствол: "
            +width+"×"+length+", глубина 10. Схема сохранена. "
            +"Включи «Сразу вниз» в меню NPC."));
        return true;
    }
}
