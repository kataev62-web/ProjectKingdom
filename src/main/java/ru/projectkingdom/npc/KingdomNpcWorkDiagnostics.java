package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import ru.projectkingdom.enterprise.EnterpriseBuildingRegistration;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Pure read-only diagnostics. No items, chunks, jobs or permissions are created.
 * Specific shortages are shown above each loaded Kingdom villager.
 */
public final class KingdomNpcWorkDiagnostics {
    private KingdomNpcWorkDiagnostics(){}
    public record Status(String message,boolean warning){
        public Status {
            if(message==null || message.isBlank())throw new IllegalArgumentException("empty status");
            if(message.length()>112)message=message.substring(0,112);
        }
    }
    private static Status warn(String s){return new Status(s,true);}
    private static Status good(String s){return new Status(s,false);}
    private static boolean axe(ItemStack s){
        return !s.isEmpty() && (s.is(ItemTags.AXES)
                ||s.getItem() instanceof AxeItem);
    }
    private static UUID company(KingdomNpcData.Worker w){
        if(w.workplaceId==null || w.workplaceId.isBlank())return null;
        try{return UUID.fromString(w.workplaceId);}
        catch(IllegalArgumentException ignored){return null;}
    }
    private static Status smith(ServerLevel level,KingdomNpcData.Worker w){
        var server=level.getServer();
        UUID firm=company(w);
        if(firm==null)return warn("Кузнец: не выбрана кузница");
        var enterprises=KingdomEnterpriseData.get(server);
        if(enterprises.kind(firm)!=EnterpriseKind.SMITHY
                ||enterprises.find(firm)==null)return warn("Кузнец: выбрано неверное предприятие");
        var room=EnterpriseBuildingRegistration.get(server).room(firm);
        if(room==null)return warn("Кузнец: привяжите здание к этой кузнице");
        if(!w.hasWorkstation)return warn("Кузнец: назначьте горн TFC");
        BlockPos forge=new BlockPos(w.workX,w.workY,w.workZ);
        if(!room.contains(forge))return warn("Кузнец: горн находится за границами здания");
        if(!level.hasChunkAt(forge))return warn("Кузнец: участок с горном не загружен");
        if(!KingdomTfcSmithWork.isForge(level,w))return warn("Кузнец: требуется настоящий горн TFC");
        boolean foundAnvil=false;
        for(int x=Math.max(room.minX(),forge.getX()-8);x<=Math.min(room.maxX(),forge.getX()+8)&&!foundAnvil;x++)
            for(int z=Math.max(room.minZ(),forge.getZ()-8);z<=Math.min(room.maxZ(),forge.getZ()+8)&&!foundAnvil;z++)
                for(int y=Math.max(room.minY(),forge.getY()-3);y<=Math.min(room.maxY(),forge.getY()+3);y++){
                    BlockPos at=new BlockPos(x,y,z);
                    if(!level.hasChunkAt(at))continue;
                    var e=level.getBlockEntity(at);
                    if(e!=null && e.getClass().getName().endsWith("AnvilBlockEntity")){
                        foundAnvil=true;break;
                    }
                }
        if(!foundAnvil)return warn("Кузнец: нужна наковальня TFC внутри здания");
        if(!w.hasSource||!w.hasDestination)return warn("Кузнец: нужны склады сырья и продукции");
        BlockPos raw=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos output=new BlockPos(w.destX,w.destY,w.destZ);
        if(!level.hasChunkAt(raw)||!level.hasChunkAt(output))
            return warn("Кузнец: склад не загружен");
        Container source=KingdomContainerAccess.resolve(level,raw);
        Container destination=KingdomContainerAccess.resolve(level,output);
        if(source==null||destination==null)
            return warn("Кузнец: склад недоступен или не совместим");
        boolean fuel=false,metal=false;
        for(int i=0;i<source.getContainerSize();i++){
            ItemStack item=source.getItem(i);
            if(KingdomTfcSmithWork.forgeFuel(item))fuel=true;
            else if(!item.isEmpty())metal=true;
        }
        if(!fuel)return warn("Кузнец: нет топлива для горна");
        if(!metal)return warn("Кузнец: нет металлического сырья");
        return good("Кузнец: горн и материалы готовы");
    }
    private static Status forestry(ServerLevel level,KingdomNpcData.Worker w){
        UUID firm=company(w);
        if(firm==null)return warn("Лесоруб: назначьте лесозаготовку");
        var areas=KingdomForestData.get(level.getServer()).of(firm);
        if(areas.isEmpty())return warn("Лесоруб: выделите лесной участок");
        var active=areas.stream().filter(a->a.enabled()&&a.dimension().equals(w.dimension)).findFirst();
        if(active.isEmpty())return warn("Лесоруб: лесной участок отключён");
        var area=active.get();
        if(!KingdomTfcForestry.validSapling(area.sapling()))
            return warn("Лесоруб: выберите породу дерева TFC");
        if(!w.hasSource||!w.hasDestination)
            return warn("Лесоруб: нужны два склада");
        BlockPos source=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(!level.hasChunkAt(source))return warn("Лесоруб: склад сырья не загружен");
        var raw=KingdomContainerAccess.resolve(level,source);
        if(raw==null)return warn("Лесоруб: склад сырья не совместим");
        boolean hasAxe=axe(w.fieldTool);
        for(int i=0;i<raw.getContainerSize()&&!hasAxe;i++)
            if(axe(raw.getItem(i)))hasAxe=true;
        if(!hasAxe)return warn("Лесоруб: положите топор в склад сырья");
        if(!KingdomTfcForestry.nativeAvailable())
            return warn("Лесоруб: не найден совместимый API рубки TFC");
        return good("Лесоруб: готов рубить деревья");
    }
    public static Status forWorker(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(level==null||npc==null||w==null)return warn("NPC: нет данных");
        if(w.safety<25)return warn("Небезопасно! Нужна защита");
        if(w.hunger<30)return warn("Нужна еда");
        if(w.energy<25)return warn("Нужен отдых");
        if(!w.hasHome)return warn("Нужен дом и кровать");
        if(w.contractPaused)return warn("Не выплачена зарплата — работа остановлена");
        if(w.employer==null && KingdomNpcFarmOwner.ownedFarm(level.getServer(),w)==null)
            return good("Ищет работу");
        if("lumberjack".equals(w.job))return forestry(level,w);
        if("smith".equals(w.job))return smith(level,w);
        if(!w.hasSource && KingdomNpcDuty.hasPhysicalExecutor(w.job))
            return warn("Для работы нужен склад сырья");
        if(!w.hasDestination && KingdomNpcDuty.hasPhysicalExecutor(w.job))
            return warn("Для работы нужен склад продукции");
        if("REPORT_BLOCKED".equals(w.autonomousIntent))
            return warn("Работа заблокирована: проверьте настройку");
        return good("Занятие: "+KingdomProfessionRegistry.title(w.job));
    }
}
