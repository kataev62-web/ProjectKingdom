package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, read-only inspection of REAL loaded blocks and inventories. */
public final class VillageStatus {
    private VillageStatus() {}
    public static final int RADIUS = 12;
    public static void report(ServerPlayer player) {
        ServerLevel level=player.serverLevel(); BlockPos origin=player.blockPosition();
        int citizens=0, hired=0, free=0, warehouses=0, claimed=0, crops=0;
        int tfcBlocks=0, loadedInventoryItems=0;
        Map<String,Integer> stock = new LinkedHashMap<>();
        var db=KingdomNpcData.get(player.getServer());
        var territory=KingdomTerritories.get(player.getServer());
        for(var worker:db.all()) {
            if(!worker.dimension.equals(level.dimension().location().toString())) continue;
            var entity=level.getEntity(worker.entityId);
            if(!(entity instanceof Villager npc)||!npc.isAlive()||npc.blockPosition().distSqr(origin)>RADIUS*RADIUS)continue;
            citizens++; if(worker.employer!=null)hired++;else free++;
        }
        for(BlockPos cursor:BlockPos.betweenClosed(origin.offset(-RADIUS,-3,-RADIUS),origin.offset(RADIUS,3,RADIUS))) {
            if(!level.hasChunkAt(cursor))continue;
            BlockPos pos=cursor.immutable();
            if(territory.owner(level,pos)!=null)claimed++;
            var state=level.getBlockState(pos);
            String blockId=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if(blockId.startsWith("tfc:"))tfcBlocks++;
            if(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock)crops++;
            if(!"не назначено".equals(territory.role(level,pos)))warehouses++;
            BlockEntity be=level.getBlockEntity(pos);
            if(be instanceof Container inv) {
                for(int slot=0;slot<inv.getContainerSize();slot++){
                    ItemStack stack=inv.getItem(slot);if(stack.isEmpty())continue;
                    loadedInventoryItems+=stack.getCount();
                    String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    stock.merge(id,stack.getCount(),Integer::sum);
                }
            }
        }
        tell(player,"Живая деревня: проверка реально загруженного мира, радиус "+RADIUS+" блоков.");
        tell(player,"Наши жители: "+citizens+" (наёмные: "+hired+", независимые: "+free+").");
        tell(player,"Размеченные узлы: "+warehouses+", блоки TFC: "+tfcBlocks+", обычные посевы: "+crops+".");
        tell(player,"В контейнерах: "+loadedInventoryItems+" настоящих предметов, видов: "+stock.size()+". Проверено участков-блоков: "+claimed+".");
        stock.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).limit(6)
            .forEach(e->tell(player,"Склад: "+e.getKey()+" ×"+e.getValue()));
        tell(player,"Производство TFC/Create: прямой исполнительный адаптер НЕ ПОДКЛЮЧЁН. Автоматический крафт запрещён.");
        tell(player,"MineColonies: жители обнаруживаются отдельно через /knpc colonies; их ИИ не перехватывается.");
    }
    private static void tell(ServerPlayer p,String message){p.sendSystemMessage(Component.literal("[Project Kingdom] "+message));}
}
