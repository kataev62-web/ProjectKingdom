package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import ru.projectkingdom.administration.*;

/**
 * Physical municipal inventory source shared by seasonal forecasts, buying
 * and courier authorization. Rooms supersede legacy chest bindings, never
 * combine both (or count double chests twice). Never force-load chunks.
 */
public final class KingdomMunicipalFoodStorage {
    private KingdomMunicipalFoodStorage(){}
    public record Slot(ServerLevel level,BlockPos pos,Container container){}
    public record Snapshot(boolean complete,List<Slot> containers) {
        public Snapshot { containers=List.copyOf(containers); }
    }

    public static Snapshot inspect(MinecraftServer server,UUID town) {
        if(server==null||town==null)return new Snapshot(false,List.of());
        var admin=KingdomAdministrationData.get(server);
        var civic=KingdomCivicsData.get(server);
        var core=admin.core(town,AdministrativeKind.CENTRAL_WAREHOUSE);
        if(core==null)return new Snapshot(false,List.of());
        ServerLevel level=level(server,core.dimension());
        if(level==null||!KingdomWarehouseMarket.get(server).activeMunicipalCore(
                level,town,AdministrativeKind.CENTRAL_WAREHOUSE))
            return new Snapshot(false,List.of());
        var rooms=KingdomCentralWarehouseRooms.get(server);
        var registered=rooms.rooms(town);
        List<Slot> result=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        boolean complete=true;
        if(!registered.isEmpty()){
            for(var room:registered){
                ServerLevel current=level(server,room.dimension());
                if(current==null||current!=level
                        ||rooms.validate(current,room)!=KingdomCentralWarehouseRooms.Result.CREATED){
                    complete=false;continue;
                }
                for(BlockPos pos:BlockPos.betweenClosed(room.min(),room.max())){
                    if(!current.getBlockState(pos).hasBlockEntity())continue;
                    Container c=KingdomContainerAccess.resolve(current,pos);
                    if(c==null)continue;
                    String key=room.dimension()+":"+pos.asLong();
                    if(!seen.add(key))continue;
                    if(current.getBlockState(pos).getBlock() instanceof ChestBlock) {
                        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                            BlockPos other=pos.relative(direction);
                            if(KingdomContainerAccess.samePhysicalStorage(current,pos,other))
                                seen.add(room.dimension()+":"+other.asLong());
                        }
                    }
                    var owner=civic.townAt(current,pos);
                    if(owner==null||!town.equals(owner.id())){complete=false;continue;}
                    result.add(new Slot(current,pos.immutable(),c));
                }
            }
        }else{
            // Compatibility for worlds with per-chest binding, before rooms.
            var legacy=admin.boundContainers(town,AdministrativeBindingRole.WAREHOUSE_STORAGE);
            if(legacy.isEmpty())complete=false;
            for(var bound:legacy){
                ServerLevel current=level(server,bound.dimension());
                BlockPos pos=BlockPos.of(bound.packedPos());
                if(current==null || current!=level || !current.hasChunkAt(pos)){
                    complete=false;continue;
                }
                var owner=civic.townAt(current,pos);
                Container c=KingdomContainerAccess.resolve(current,pos);
                if(owner==null||!town.equals(owner.id())||c==null){
                    complete=false;continue;
                }
                if(seen.add(bound.dimension()+":"+pos.asLong()))
                    result.add(new Slot(current,pos.immutable(),c));
            }
        }
        return new Snapshot(complete&&!result.isEmpty(),result);
    }

    public static boolean destination(MinecraftServer server,UUID town,String dimension,BlockPos pos) {
        Snapshot all=inspect(server,town);
        if(!all.complete())return false;
        return all.containers().stream().anyMatch(x ->
                x.pos().equals(pos)
                && x.level().dimension().location().toString().equals(dimension));
    }

    private static ServerLevel level(MinecraftServer server,String dim) {
        for(ServerLevel level:server.getAllLevels())
            if(level.dimension().location().toString().equals(dim))return level;
        return null;
    }
}
