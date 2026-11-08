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
        ACTIVE.put(player.getUUID(),new Pending(mine,binding.dimension(),null,
            player.serverLevel().getGameTime()+2400));
        ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,null,true,
                KingdomMineSettings.get(player.getServer()).levels(mine)*5);
        player.sendSystemMessage(Component.literal(
            "[Шахта] Выдели два противоположных угла ЦЕНТРАЛЬНОГО СТВОЛА на одном уровне: "
            +"Shift+ПКМ пустой рукой. Размер 7×7 ... 12×12, шаг уровней 5 блоков. "
            +"Участок должен принадлежать владельцу шахты."));
        return true;
    }

    public static boolean select(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)
            ||event.getHand()!=InteractionHand.MAIN_HAND
            ||!ACTIVE.containsKey(player.getUUID()))return false;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
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
            player.sendSystemMessage(Component.literal("[Шахта] Используй Shift+ПКМ пустой рукой."));
            return true;
        }
        BlockPos at=event.getPos();
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
                ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,at,true,
                KingdomMineSettings.get(player.getServer()).levels(p.mine())*5);
            player.sendSystemMessage(Component.literal("[Шахта] Первый угол "
                +at.toShortString()+". Отметь второй угол по диагонали."));
            return true;
        }
        BlockPos first=p.first();
        int minX=Math.min(first.getX(),at.getX()),maxX=Math.max(first.getX(),at.getX());
        int minZ=Math.min(first.getZ(),at.getZ()),maxZ=Math.max(first.getZ(),at.getZ());
        int width=maxX-minX+1,length=maxZ-minZ+1;
        var core=firms.core(p.mine());
        if(core==null||!core.active()||!core.dimension().equals(p.dimension())
            ||width<7||length<7||width>12||length>12){
            player.sendSystemMessage(Component.literal("[Шахта] Нужен прямоугольник от 7×7 до 12×12. Повтори второй угол."));
            return true;
        }
        // Clicked blocks can have different heights. The shaft begins one
        // block above the higher corner, where the miner will stand.
        int surfaceY=Math.max(first.getY(),at.getY())+1;
        var territory=KingdomTerritories.get(player.getServer());
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            for(int y=surfaceY-KingdomMineSettings.get(player.getServer()).levels(p.mine())*5;
                    y<=surfaceY;y++) {
                BlockPos cell=new BlockPos(x,y,z);
                if(!level.hasChunkAt(cell)
                    ||!territory.canUse(level,cell,company.owner())){
                    player.sendSystemMessage(Component.literal("[Шахта] Вся область до заданной глубины должна быть загружена и принадлежать владельцу."));
                    return true;
                }
            }
        }
        var area=new KingdomMineSettings.Shaft(minX,maxX,minZ,maxZ,
            surfaceY,p.dimension());
        // The enterprise core does not have to touch the south wall and
        // does not need to share the selected area's Y coordinate.
        if(!KingdomMineSettings.get(player.getServer()).setShaft(p.mine(),area))return true;
        cancel(player.getUUID());
        ru.projectkingdom.network.KingdomVolumeSelectionPacket.send(player,
            ru.projectkingdom.network.KingdomVolumeSelectionPacket.MINE,null,false);
        player.sendSystemMessage(Component.literal("[Шахта] Центральный ствол: "
            +width+"×"+length+", глубина "
            +(KingdomMineSettings.get(player.getServer()).levels(p.mine())*5)
            +". Схема сохранена. Вход рассчитан по центру южной стены. "
            +"Режим и направление выбираются в блоке предприятия."));
        return true;
    }
}
