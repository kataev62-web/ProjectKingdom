package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;

/** Resolves city and nation from actual placed enterprise core, never from a client label. */
public final class KingdomAdminEnterpriseService {
    public record Listing(UUID enterprise,UUID owner,String name,UUID townId,String townName,
                          UUID nationId,String nationName,boolean loaded) {}
    public record Closure(KingdomEnterpriseData.ClosureStatus status,Listing listing) {}
    private KingdomAdminEnterpriseService(){}

    public static Listing location(MinecraftServer server,UUID enterprise){
        if(server==null||enterprise==null)return null;
        var companies=KingdomEnterpriseData.get(server);
        var firm=companies.find(enterprise);
        var binding=companies.core(enterprise);
        if(firm==null||binding==null||!binding.active())return null;
        for(ServerLevel level:server.getAllLevels()){
            if(!level.dimension().location().toString().equals(binding.dimension()))continue;
            BlockPos at=BlockPos.of(binding.packedPos());
            // Plot is registry-backed, so it can be inspected without loading the chunk.
            var plot=KingdomCivicsData.get(server).plot(level,at);
            if(plot==null)return null; // outside the city: cannot mislabel private wilderness as civic property
            var town=KingdomCivicsData.get(server).townById(plot.town());
            if(town==null)return null;
            var nation=town.nation()==null?null:KingdomCivicsData.get(server).nations().stream()
                    .filter(n->n.id().equals(town.nation())).findFirst().orElse(null);
            boolean loaded=level.hasChunkAt(at);
            if(loaded){
                var core=level.getBlockEntity(at);
                if(!(core instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity actual)
                        ||!enterprise.equals(actual.enterpriseId()))return null;
            }
            return new Listing(firm.id(),firm.owner(),firm.name(),town.id(),town.name(),
                    nation==null?null:nation.id(),nation==null?"Независимое поселение":nation.name(),loaded);
        }
        return null;
    }

    public static List<Listing> list(ServerPlayer operator){
        if(operator==null||!operator.hasPermissions(2))return List.of();
        MinecraftServer server=operator.getServer();
        List<Listing> rows=new ArrayList<>();
        for(var firm:KingdomEnterpriseData.get(server).all()){
            var entry=location(server,firm.id());
            if(entry!=null)rows.add(entry);
        }
        rows.sort(Comparator.comparing(Listing::nationName)
                .thenComparing(Listing::townName).thenComparing(Listing::name)
                .thenComparing(x->x.enterprise().toString()));
        // Bounded menu packet, never unlimited payload sent to a client.
        return List.copyOf(rows.subList(0,Math.min(rows.size(),512)));
    }
    public static Closure close(ServerPlayer operator,UUID firmId){
        if(operator==null||!operator.hasPermissions(2))return null;
        var server=operator.getServer();
        var located=location(server,firmId);
        if(located==null)return null;
        var companies=KingdomEnterpriseData.get(server);
        var binding=companies.core(firmId);
        if(binding==null||!binding.active()||!located.loaded())return
                new Closure(KingdomEnterpriseData.ClosureStatus.STORAGE_UNLOADED,located);
        // Do not perform a remote mutation in another dimension via a fake client position.
        if(!operator.serverLevel().dimension().location().toString().equals(binding.dimension()))
            return new Closure(KingdomEnterpriseData.ClosureStatus.NOT_AT_CORE,located);
        var status=companies.adminCloseBusiness(operator,firmId,BlockPos.of(binding.packedPos()));
        if(status==KingdomEnterpriseData.ClosureStatus.CLOSED){
            KingdomAdminEnterpriseAudit.get(server).recordVerified(
                    new KingdomAdminEnterpriseAudit.Entry(operator.getUUID(),firmId,located.owner(),
                            located.townId(),located.nationId(),located.name(),
                            Math.max(0,server.overworld().getDayTime()/24000L)));
        }
        return new Closure(status,located);
    }
}
