package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only survey of a LIVE loaded part of the world. Does not create products,
 * assume vanilla recipes for TFC, claim a MineColonies colony or change ownership.
 * Use the measurements as the entry gate to an actual autonomous village.
 */
public final class LivingVillageAudit {
    private LivingVillageAudit() {}
    private static final int RADIUS = 16;

    public record Snapshot(int kingdomCitizens, int colonyCitizens, int vanillaVillagers,
                           int containers, int totalItems, int tfcBlocks, int minecoloniesBlocks,
                           int createBlocks, int cropBlocks, Map<String,Integer> items) {
        public String summary() {
            return "NPC Kingdom="+kingdomCitizens+", MineColonies="+colonyCitizens+
                ", обычные="+vanillaVillagers+"; контейнеры="+containers+
                ", предметы="+totalItems+"; TFC-блоки="+tfcBlocks+
                ", MineColonies-блоки="+minecoloniesBlocks+
                ", Create-блоки="+createBlocks+", посевы="+cropBlocks;
        }
    }

    public static Snapshot scan(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int chests=0, itemsCount=0, tfc=0, colony=0, create=0, crops=0;
        Map<String,Integer> items=new HashMap<>();
        // Audits ONLY loaded chunks. Avoid force-loading and expensive world generation.
        for (int dx=-RADIUS; dx<=RADIUS; dx++) for (int dz=-RADIUS; dz<=RADIUS; dz++) {
            for (int dy=-8; dy<=8; dy++) {
                BlockPos pos=origin.offset(dx,dy,dz);
                if(!level.hasChunkAt(pos)) continue;
                ResourceLocation blockId=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                if(blockId!=null) {
                    switch(blockId.getNamespace()) {
                        case "tfc" -> tfc++;
                        case "minecolonies", "structurize" -> colony++;
                        case "create" -> create++;
                    }
                    String path=blockId.getPath();
                    if (path.contains("crop") || path.contains("farmland")) crops++;
                }
                BlockEntity be=level.getBlockEntity(pos);
                if(be instanceof Container c) {
                    chests++;
                    for(int slot=0;slot<c.getContainerSize();slot++) {
                        ItemStack stack=c.getItem(slot);
                        if(stack.isEmpty()) continue;
                        itemsCount+=stack.getCount();
                        ResourceLocation key=BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if(key!=null) items.merge(key.toString(),stack.getCount(),Integer::sum);
                    }
                }
            }
        }
        List<Entity> nearby=level.getEntities(player,player.getBoundingBox().inflate(RADIUS),
                entity->entity instanceof Villager || MineColoniesBridge.isCitizen(entity));
        int mineCitizens=0, villageCitizens=0, kingdomCitizens=0;
        KingdomNpcData database=KingdomNpcData.get(player.getServer());
        for(Entity e: nearby) {
            if(MineColoniesBridge.isCitizen(e)) { mineCitizens++; continue; }
            if(e instanceof Villager) {
                if(database.workers.values().stream().anyMatch(w->e.getUUID().equals(w.entityId))) kingdomCitizens++;
                else villageCitizens++;
            }
        }
        return new Snapshot(kingdomCitizens,mineCitizens,villageCitizens,chests,itemsCount,tfc,colony,create,crops,Map.copyOf(items));
    }

    public static void report(ServerPlayer player) {
        Snapshot s=scan(player);
        player.sendSystemMessage(Component.literal("[Живая деревня] Обследование радиуса 16 блоков: "+s.summary()));
        boolean tfc=ModList.get().isLoaded("tfc");
        boolean colonies=ModList.get().isLoaded("minecolonies");
        player.sendSystemMessage(Component.literal("[Живая деревня] TFC="+tfc+", MineColonies="+colonies+
            ", Create="+ModList.get().isLoaded("create")+". Учтены только загруженные блоки и реальные предметы."));
        if(s.colonyCitizens()==0) player.sendSystemMessage(Component.literal("[Живая деревня] Нет жителей MineColonies поблизости: сначала нужна рабочая колония."));
        if(s.containers()==0) player.sendSystemMessage(Component.literal("[Живая деревня] Не найдены хранилища в зоне обследования."));
        if(s.tfcBlocks()==0) player.sendSystemMessage(Component.literal("[Живая деревня] Вокруг нет оборудования/материалов TFC для проверки производства."));
        player.sendSystemMessage(Component.literal("[Живая деревня] Это диагностика, НЕ автоматизация. Рецепты TFC, задания MineColonies и автономная торговля ещё не подключены."));
    }
}
