package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Read-only site diagnostics. Never consumes supplies or generates chunks. */
public final class KingdomConstructionDiagnostics {
    private KingdomConstructionDiagnostics() {}
    public static String nextStep(MinecraftServer server, KingdomConstructionData.Project site) {
        var type=KingdomBuildingTypes.type(site.design());
        if(type==null)return "Неизвестный проект";
        var plan=KingdomBuildingTypes.design(type).blocks();
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
        ResourceLocation id=ResourceLocation.tryParse(cell.material());
        if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))return "Неизвестный блок: "+cell.material();
        var block=BuiltInRegistries.BLOCK.get(id);
        if(level.getBlockState(target).is(block))return "Ожидается подтверждение установленного блока";
        if(!level.getBlockState(target).isAir())return "Убери препятствие: "+target.toShortString();
        Item item=block.asItem();
        if(item==Items.AIR)return "У блока нет предмета: "+cell.material();
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
        var type=KingdomBuildingTypes.type(site.design());
        if(type==null)return "Неизвестный проект";
        var plan=KingdomBuildingTypes.design(type).blocks();
        ServerLevel level=null;
        for(var candidate:server.getAllLevels())if(candidate.dimension().location().toString().equals(site.dimension())){level=candidate;break;}
        if(level==null)return "Измерение недоступно";
        BlockPos marker=new BlockPos(site.x()-2,site.y(),site.z()-2);
        Container depot=level.hasChunkAt(marker)?supplies(level,marker):null;
        Map<Item,Integer> needed=new LinkedHashMap<>();
        for(int i=KingdomConstructionData.get(server).progress(site.id());i<plan.size();i++) {
            var cell=plan.get(i);
            ResourceLocation id=ResourceLocation.tryParse(cell.material());
            if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))continue;
            var block=BuiltInRegistries.BLOCK.get(id);
            BlockPos target=new BlockPos(site.x()+cell.x(),site.y()+cell.y(),site.z()+cell.z());
            if(level.hasChunkAt(target)&&level.getBlockState(target).is(block))continue;
            Item item=block.asItem();if(item!=Items.AIR)needed.merge(item,1,Integer::sum);
        }
        if(depot!=null)for(int i=0;i<depot.getContainerSize();i++) {
            var stack=depot.getItem(i);
            if(!stack.isEmpty())needed.computeIfPresent(stack.getItem(),(item,count)->Math.max(0,count-stack.getCount()));
        }
        StringBuilder result=new StringBuilder();int types=0;
        for(var entry:needed.entrySet())if(entry.getValue()>0) {
            if(types++>=5){result.append("; ...");break;}
            if(result.length()>0)result.append("; ");
            result.append(BuiltInRegistries.ITEM.getKey(entry.getKey())).append(" ×").append(entry.getValue());
        }
        return result.length()==0?"Все материалы на складе":"Не хватает: "+result;
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
