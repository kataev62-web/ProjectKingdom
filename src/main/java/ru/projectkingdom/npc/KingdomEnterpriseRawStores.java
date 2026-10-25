package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;

/**
 * Existing, physically accessible RAW containers of one enterprise.
 * This is a view, not a second inventory or a chunk-loading registry.
 */
public final class KingdomEnterpriseRawStores {
    private KingdomEnterpriseRawStores() {}

    public record Source(BlockPos pos, Container inventory, int firstFreeSlot) {}

    public static List<Source> loaded(ServerLevel level, UUID enterprise) {
        var firms = KingdomEnterpriseData.get(level.getServer());
        var firm = firms.find(enterprise);
        if(firm == null) return List.of();
        String dimension = level.dimension().location().toString();
        Map<BlockPos,Boolean> locations = new LinkedHashMap<>();
        var registered = firms.storageBinding(enterprise,EnterpriseBindingRole.RAW_STORAGE);
        if(registered != null && dimension.equals(registered.dimension()))
            locations.put(registered.pos(),false);
        var plans = KingdomNpcWorkshopData.get(level.getServer());
        for(var worker:KingdomNpcData.get(level.getServer()).all()) {
            if(!worker.hasSource || !dimension.equals(worker.dimension)
                    || !firm.owner().equals(worker.employer))continue;
            var plan = plans.plan(worker.entityId);
            if(plan==null || !enterprise.equals(plan.enterprise()))continue;
            BlockPos pos=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
            // All worker recipe layouts reserve the first nine slots even when
            // that same chest is also the company's registered main warehouse.
            locations.put(pos,true);
        }
        List<Source> result = new ArrayList<>();
        var territory = KingdomTerritories.get(level.getServer());
        for(var entry:locations.entrySet()) {
            BlockPos pos=entry.getKey();
            if(!level.hasChunkAt(pos) || firms.storageClaimedByOther(enterprise,dimension,pos))continue;
            boolean isRegisteredSource=registered!=null && dimension.equals(registered.dimension())
                    && registered.pos().equals(pos);
            if(!((isRegisteredSource && territory.canUse(level,pos,firm.owner()))
                    || territory.usable(level,pos,firm.owner(),"raw")))continue;
            if(!(level.getBlockEntity(pos) instanceof Container c))continue;
            int first = entry.getValue()?9:0;
            if(c.getContainerSize()<=first)continue;
            result.add(new Source(pos.immutable(),c,first));
        }
        return List.copyOf(result);
    }

    /** Do not trust unloaded or re-bound chests; every pickup is authorized again. */
    public static Source find(ServerLevel level, UUID enterprise, BlockPos pos) {
        for(Source entry:loaded(level,enterprise))
            if(entry.pos().equals(pos))return entry;
        return null;
    }
}
