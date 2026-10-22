package ru.projectkingdom.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Explicit opt-in for testing the existing physical NPC field worker without touching MineColonies. */
public final class VillageWorkActivation {
    private VillageWorkActivation() {}
    public static int activate(ServerPlayer player) {
        var data=KingdomNpcData.get(player.getServer());
        int assigned=0, skipped=0;
        for(var w:data.all()) {
            if(w.employer==null || !w.employer.equals(player.getUUID()) || w.contractPaused || w.ageYears<18){skipped++;continue;}
            if(!w.dimension.equals(player.level().dimension().location().toString()))continue;
            if(!("farmer".equals(w.job)||"lumberjack".equals(w.job)||"miner".equals(w.job))){skipped++;continue;}
            var entity=player.serverLevel().getEntity(w.entityId);
            if(!(entity instanceof Villager npc)||!npc.isAlive()||npc.distanceToSqr(player)>32*32){skipped++;continue;}
            if(w.hasWorkstation && !KingdomTerritories.get(player.getServer()).canUse(player.serverLevel(),
                 new net.minecraft.core.BlockPos(w.workX,w.workY,w.workZ),player.getUUID())){skipped++;continue;}
            if(!"work".equals(w.order)) {w.order="work";assigned++;}
        }
        if(assigned>0)data.setDirty();
        player.sendSystemMessage(Component.literal("[Project Kingdom] Работники переведены в режим работы: "+assigned+
            "; пропущено: "+skipped+". Нужны реальные инструменты и разрешённые участки."));
        return assigned;
    }
}
