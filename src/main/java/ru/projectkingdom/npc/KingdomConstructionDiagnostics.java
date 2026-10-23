package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Read-only site diagnostics. Never consumes supplies or generates chunks. */
public final class KingdomConstructionDiagnostics {
    private KingdomConstructionDiagnostics() {}
    public record Material(Item item, int required, int available) {
        public int missing() { return Math.max(0, required - available); }
    }

    /** Authoritative, read-only bill of materials for the Kingdom icon UI. */
    public static List<Material> materials(MinecraftServer server, KingdomConstructionData.Project site) {
        if (server == null || site == null) return List.of();
        var design = KingdomConstructionPlans.resolve(server, site.design());
        if (design == null) return List.of();
        ServerLevel level = null;
        for (var candidate : server.getAllLevels())
            if (candidate.dimension().location().toString().equals(site.dimension())) {
                level = candidate; break;
            }
        if (level == null) return List.of();
        BlockPos marker = new BlockPos(site.x()-2, site.y(), site.z()-2);
        Container depot = level.hasChunkAt(marker) ? supplies(level, marker) : null;
        Map<Item,Integer> needed = new LinkedHashMap<>();
        int progress = KingdomConstructionData.get(server).progress(site.id());
        for (int i = progress; i < design.blocks().size(); i++) {
            var cell = design.blocks().get(i);
            net.minecraft.world.level.block.state.BlockState state;
            try { state = KingdomBlockStateCodec.parse(cell.material()); }
            catch (IllegalArgumentException invalid) { continue; }
            if (!KingdomBlockStateCodec.buildable(state)) continue;
            BlockPos target = new BlockPos(site.x()+cell.x(),site.y()+cell.y(),site.z()+cell.z());
            if (level.hasChunkAt(target) && level.getBlockState(target).equals(state)) continue;
            needed.merge(state.getBlock().asItem(), 1, Integer::sum);
        }
        Map<Item,Integer> stock = new LinkedHashMap<>();
        if (depot != null) for (int i=0;i<depot.getContainerSize();i++) {
            var stack=depot.getItem(i);
            if (!stack.isEmpty()) stock.merge(stack.getItem(),stack.getCount(),Integer::sum);
        }
        List<Material> rows = new ArrayList<>();
        for (var entry : needed.entrySet())
            rows.add(new Material(entry.getKey(),entry.getValue(),stock.getOrDefault(entry.getKey(),0)));
        return List.copyOf(rows);
    }
    public static String nextStep(MinecraftServer server, KingdomConstructionData.Project site) {
        var design=KingdomConstructionPlans.resolve(server,site.design());
        if(design==null)return "Файл строительной схемы недоступен";
        var plan=design.blocks();
        int index=KingdomConstructionData.get(server).progress(site.id());
        if(index>=plan.size())return "Постройка завершена";
        ServerLevel level=null;
        for(var candidate:server.getAllLevels())if(candidate.dimension().location().toString().equals(site.dimension())){level=candidate;break;}
        if(level==null)return "Измерение недоступно";
        BlockPos marker=new BlockPos(site.x()-2,site.y(),site.z()-2);
        if(!level.hasChunkAt(marker))return "Прогрузи чанк строительной кафедры";
        if(!KingdomConstructionMarkers.isMarker(level,marker))return "Верни чертёжный стол или строительную кафедру";
        Container supplies=supplies(level,marker);
        if(supplies==null)return "Поставь сундук или бочку вплотную к кафедре";
        var cell=plan.get(index);
        BlockPos target=new BlockPos(site.x()+cell.x(),site.y()+cell.y(),site.z()+cell.z());
        if(!level.hasChunkAt(target))return "Прогрузи чанк блока "+target.toShortString();
        if(!KingdomConstructionAccess.canBuild(server,level,target,site.owner()))return "Нет прав на блок "+target.toShortString();
        net.minecraft.world.level.block.state.BlockState state;
        try {state=KingdomBlockStateCodec.parse(cell.material());}
        catch(IllegalArgumentException error){return "Неизвестный блок: "+cell.material();}
        if(level.getBlockState(target).equals(state))return "Ожидается подтверждение установленного блока";
        if (!level.getBlockState(target).isAir()
                && !KingdomConstructionWork.canClearObstacle(level,target,level.getBlockState(target)))
            return "Защищённый блок на площадке: "+target.toShortString();
        if (!level.getBlockState(target).isAir())
            return "Препятствие будет автоматически разобрано при установке следующего блока";
        Item item=state.getBlock().asItem();
        if(!KingdomBlockStateCodec.buildable(state))return "Нельзя строить из предмета: "+cell.material();
        int available=0;
        for(int i=0;i<supplies.getContainerSize();i++)if(supplies.getItem(i).is(item))available+=supplies.getItem(i).getCount();
        if(available==0)return "Нет материала: "+cell.material();
        boolean builder=false;
        var workers=KingdomNpcData.get(server);
        for(var worker:workers.all()) {
            if(!site.owner().equals(worker.employer)||!site.dimension().equals(worker.dimension)
                ||!"builder".equals(worker.job)
                ||!KingdomNpcDuty.mayExecute(worker.job,worker.order,
                    worker.autonomousIntent,KingdomNpcWorkday.employed(server,worker)))continue;
            var entity=level.getEntity(worker.entityId);
            if(entity instanceof net.minecraft.world.entity.npc.Villager villager && villager.isAlive()
                && villager.blockPosition().distSqr(marker)<=48*48){builder=true;break;}
        }
        return builder?"Материалы есть; строитель выполняет заказ":"Назначь строителя с договором; дневная смена, радиус 48 блоков";
    }
    public static String missingMaterials(MinecraftServer server, KingdomConstructionData.Project site) {
        var rows=materials(server,site);
        int missing=0;long count=0;
        for (var row:rows) if (row.missing()>0) { missing++;count+=row.missing(); }
        return missing==0?"Все материалы в соседнем складе":
            "Недостаёт "+count+" блоков ("+missing+" видов). Открой «Материалы», чтобы увидеть названия и значки.";
    }
    private static Container supplies(ServerLevel level,BlockPos marker) {
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            BlockPos at=marker.relative(direction);
            if(!level.hasChunkAt(at))continue;
            BlockEntity entity=level.getBlockEntity(at);
            if(entity instanceof Container container)return container;
        }
        return null;
    }
}
