package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * NPC ownership must originate in the EXISTING trusted enterprise/deed ledger.
 * A worker does not become an owner merely by setting workplaceId or job=farmer.
 * A self-employed farmer has no phantom wage contract or player impersonation.
 */
public final class KingdomNpcFarmOwner {
    private KingdomNpcFarmOwner(){}

    public static UUID ownedFarm(MinecraftServer server,KingdomNpcData.Worker npc) {
        if(npc==null||npc.entityId==null
            ||!("farmer".equals(npc.job)||"breeder".equals(npc.job))||npc.ageYears<18)
            return null;
        var registry=KingdomNpcData.get(server);
        boolean exists=registry.all().stream().anyMatch(w->w.entityId.equals(npc.entityId));
        if(!exists)return null;
        var enterprises=KingdomEnterpriseData.get(server);
        for(var enterprise:enterprises.ownedAll(npc.entityId)) {
            if(enterprises.kind(enterprise.id())!=EnterpriseKind.FARM)continue;
            if(!npc.workplaceId.isBlank()&&!npc.workplaceId.equals(enterprise.id().toString()))
                continue;
            var core=enterprises.core(enterprise.id());
            if(core==null||!core.active()||core.kind()!=EnterpriseKind.FARM
                    ||!core.owner().equals(npc.entityId))continue;
            return enterprise.id();
        }
        return null;
    }

    public static UUID workOwner(ServerLevel level,KingdomNpcData.Worker npc) {
        return ownedFarm(level.getServer(),npc)!=null ? npc.entityId : npc.employer;
    }
}
