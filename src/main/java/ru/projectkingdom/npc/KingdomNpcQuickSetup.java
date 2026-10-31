package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * One-click profession setup. Reuses a real enterprise and its real chests;
 * DOES NOT claim land, create stock, bypass contracts or mint output.
 * The old manual bindings remain an advanced override.
 */
public final class KingdomNpcQuickSetup {
    private KingdomNpcQuickSetup(){}
    public record Result(boolean ready,String message){}

    public static boolean compatible(String job,EnterpriseKind kind){
        if(job==null||kind==null)return false;
        return switch(job){
            case "farmer","breeder" -> kind==EnterpriseKind.FARM;
            case "miner" -> kind==EnterpriseKind.MINE;
            case "lumberjack" -> kind==EnterpriseKind.LUMBER;
            case "smith" -> kind==EnterpriseKind.SMITHY;
            case "baker","cook","butcher" -> kind==EnterpriseKind.BAKERY;
            case "potter" -> kind==EnterpriseKind.POTTERY;
            case "weaver" -> kind==EnterpriseKind.TEXTILE;
            case "tanner" -> kind==EnterpriseKind.TANNERY;
            case "builder","carpenter","mason" -> kind==EnterpriseKind.CONSTRUCTION;
            case "courier" -> kind==EnterpriseKind.COURIER;
            // Innkeepers have schedules, but no safe physical executor yet.
            case "innkeeper" -> false;
            default -> false;
        };
    }
    private static boolean workshop(String job){
        return KingdomNpcDuty.kind(job)==KingdomNpcDuty.Kind.WORKSHOP;
    }
    private static boolean station(String job,String id){
        if(!workshop(job))return false;
        if("smith".equals(job))return "tfc:charcoal_forge".equals(id)
            ||id.startsWith("tfc:metal/anvil/");
        if("weaver".equals(job))return "tfc:loom".equals(id);
        if("tanner".equals(job))return "tfc:barrel".equals(id);
        if("baker".equals(job))return "tfc:quern".equals(id)
            ||"tfc:grill".equals(id)||"tfc:pot".equals(id)
            ||"minecraft:crafting_table".equals(id);
        if("cook".equals(job)||"butcher".equals(job))return
            "tfc:grill".equals(id)||"tfc:pot".equals(id)
            ||"minecraft:crafting_table".equals(id);
        return "minecraft:crafting_table".equals(id);
    }
    /** Prefer the actual native TFC machine over a generic vanilla crafting table. */
    private static int stationPriority(String job,String id) {
        if("smith".equals(job))
            return "tfc:charcoal_forge".equals(id)?0
                :id.startsWith("tfc:metal/anvil/")?1:50;
        if("minecraft:crafting_table".equals(id))return 10;
        return "tfc:quern".equals(id) ? 0 : "tfc:pot".equals(id) ? 1
            : "tfc:grill".equals(id) ? 2 : 0;
    }
    private static boolean coreValid(ServerLevel level,
        KingdomEnterpriseData enterprises,KingdomEnterpriseData.Enterprise business){
        var core=enterprises.core(business.id());
        if(core==null||!core.active()
            ||!core.dimension().equals(level.dimension().location().toString())
            ||core.kind()!=enterprises.kind(business.id())
            ||!core.owner().equals(business.owner()))return false;
        BlockPos at=BlockPos.of(core.packedPos());
        if(!level.hasChunkAt(at))return false;
        if(!(level.getBlockEntity(at) instanceof EnterpriseCoreBlockEntity block))
            return false;
        return block.bound()&&business.id().equals(block.enterpriseId())
            &&block.kind()==core.kind()&&business.owner().equals(block.owner());
    }
    private static KingdomEnterpriseData.Enterprise choice(ServerPlayer player,
        KingdomNpcData.Worker worker,KingdomEnterpriseData enterprises){
        var level=player.serverLevel();
        var owned=enterprises.ownedAll(player.getUUID()).stream()
            .filter(f->compatible(worker.job,enterprises.kind(f.id())))
            .filter(f->coreValid(level,enterprises,f)).toList();
        if(owned.isEmpty())return null;
        for(var firm:owned)if(firm.id().toString().equals(worker.workplaceId))return firm;
        var selected=enterprises.owned(player.getUUID());
        if(selected!=null&&owned.contains(selected))return selected;
        return owned.stream().min(Comparator.comparingDouble(f->BlockPos
            .of(enterprises.core(f.id()).packedPos()).distSqr(player.blockPosition()))).orElse(null);
    }
    private static boolean acceptable(ServerLevel level,BlockPos position,
        UUID owner,String role,UUID firm,KingdomEnterpriseData enterprises) {
        if(!level.hasChunkAt(position)
            ||!KingdomTerritories.get(level.getServer()).canUse(level,position,owner)
            ||enterprises.storageClaimedByOther(firm,
                level.dimension().location().toString(),position))return false;
        String actual=KingdomTerritories.get(level.getServer()).role(level,position);
        return role.equals(actual)||"не назначено".equals(actual);
    }
    private static BlockPos storage(ServerLevel level,KingdomEnterpriseData firms,
        UUID enterprise,UUID owner,BlockPos center,EnterpriseBindingRole role,
        BlockPos excluded){
        String label=role==EnterpriseBindingRole.RAW_STORAGE?"raw":"finished";
        var registered=firms.storageBinding(enterprise,role);
        if(registered!=null&&registered.dimension().equals(level.dimension().location().toString())
                &&!registered.pos().equals(excluded)
                &&registered.pos().distSqr(center)<=36
                &&acceptable(level,registered.pos(),owner,label,enterprise,firms)
                &&NpcLogistics.isContainer(level,registered.pos()))return registered.pos();
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(int x=-7;x<=7;x++)for(int y=-3;y<=3;y++)for(int z=-7;z<=7;z++){
            BlockPos p=center.offset(x,y,z);
            if(p.equals(excluded)||p.distSqr(center)>36||!level.hasChunkAt(p)
                ||!NpcLogistics.isContainer(level,p)
                ||!acceptable(level,p,owner,label,enterprise,firms))continue;
            double score=p.distSqr(center);
            if(score<distance){best=p.immutable();distance=score;}
        }
        return best;
    }
    private static BlockPos workstation(ServerLevel level,String job,BlockPos center,
                                         UUID owner) {
        BlockPos best=null;double distance=Double.MAX_VALUE;
        int priority=Integer.MAX_VALUE;
        var land=KingdomTerritories.get(level.getServer());
        for(int x=-7;x<=7;x++)for(int y=-3;y<=3;y++)for(int z=-7;z<=7;z++){
            BlockPos pos=center.offset(x,y,z);
            if(!level.hasChunkAt(pos)||!land.canUse(level,pos,owner))continue;
            String id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            if(!station(job,id))continue;
            int rank=stationPriority(job,id);
            double d=pos.distSqr(center);
            if(rank<priority || rank==priority && d<distance){
                priority=rank;distance=d;best=pos.immutable();
            }
        }
        return best;
    }
    private static boolean register(ServerLevel level,KingdomEnterpriseData firms,
                                    UUID firm,UUID owner,BlockPos pos,String role,
                                    EnterpriseBindingRole binding){
        if(pos==null||!acceptable(level,pos,owner,role,firm,firms))return false;
        var territory=KingdomTerritories.get(level.getServer());
        if(!territory.register(level,pos,owner,role))return false;
        // One authoritative company binding can be shared by ALL its workers.
        // A pre-existing binding from the same company may legitimately refer
        // elsewhere; do not overwrite that manual preference.
        if(firms.storageBinding(firm,binding)==null)
            firms.bindStorage(owner,firm,binding,
                level.dimension().location().toString(),pos);
        return true;
    }
    /** Human readable result: nothing is granted when no legitimate business exists. */
    public static Result configure(ServerPlayer player,KingdomNpcData.Worker worker) {
        if(player==null||worker==null ||worker.ageYears<18)
            return new Result(false,"Для работы нужен совершеннолетний житель.");
        if(worker.employer!=null&&!worker.employer.equals(player.getUUID())
            &&!player.hasPermissions(2))
            return new Result(false,"Этот NPC нанят другим владельцем.");
        var server=player.getServer();
        var firms=KingdomEnterpriseData.get(server);
        var business=choice(player,worker,firms);
        if(business==null)return new Result(false,
            "Нет действующего предприятия для профессии «"
            +KingdomProfessionRegistry.title(worker.job)+"». Установи подходящее ядро на своей земле.");
        ServerLevel level=player.serverLevel();
        var core=firms.core(business.id());
        BlockPos center=BlockPos.of(core.packedPos());
        UUID owner=business.owner();
        var land=KingdomTerritories.get(server);
        if(!land.canUse(level,center,owner))
            return new Result(false,"Территория ядра не закреплена за владельцем. Оформи участок в ратуше.");
        BlockPos machine=null;
        if(workshop(worker.job)){
            // Preserve a correctly assigned TFC machine, otherwise find one.
            if(worker.hasWorkstation){
                var previous=new BlockPos(worker.workX,worker.workY,worker.workZ);
                if(level.hasChunkAt(previous)&&land.canUse(level,previous,owner)
                    &&station(worker.job,BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(previous).getBlock()).toString()))
                    machine=previous;
            }
            if(machine==null)machine=workstation(level,worker.job,center,owner);
        }
        // Real TFC work requires physical chests within six blocks of the machine.
        BlockPos storageCenter=machine!=null?machine:center;
        // Builders obtain their materials from a real construction project.
        // Couriers work with each real delivery order. Neither needs two fixed
        // personal warehouse bindings merely to start a shift.
        boolean warehousesRequired=KingdomNpcDuty.kind(worker.job)
            !=KingdomNpcDuty.Kind.CONSTRUCTION
            && KingdomNpcDuty.kind(worker.job)!=KingdomNpcDuty.Kind.DELIVERY;
        BlockPos source=warehousesRequired
            ?storage(level,firms,business.id(),owner,storageCenter,
                EnterpriseBindingRole.RAW_STORAGE,null):null;
        BlockPos dest=warehousesRequired
            ?storage(level,firms,business.id(),owner,storageCenter,
                EnterpriseBindingRole.FINISHED_STORAGE,source):null;
        if(warehousesRequired&&(source==null||dest==null))return new Result(false,
            "Возле «"+business.name()+"» не найдены ДВА разных склада на своей земле. "
            +"Поставь сундуки в пределах 7 блоков от ядра или привяжи склады предприятия.");
        if(warehousesRequired&&
            (!register(level,firms,business.id(),owner,source,"raw",EnterpriseBindingRole.RAW_STORAGE)
            ||!register(level,firms,business.id(),owner,dest,"finished",
                EnterpriseBindingRole.FINISHED_STORAGE)))
            return new Result(false,"Склады заняты другой ролью или предприятием. "
                +"Выбери свободные сундуки на своей земле.");
        if("smith".equals(worker.job)){
            var building=ru.projectkingdom.enterprise.EnterpriseBuildingRegistration.get(server)
                    .room(business.id());
            if(building==null)return new Result(false,
                    "Сначала зарегистрируй здание кузницы через производственный блок.");
            if(machine==null||!KingdomTfcSmithWork.inside(building,machine))
                return new Result(false,
                    "В зарегистрированном здании не найден горн TFC.");
        }
        if(machine!=null && !land.register(level,machine,owner,"workstation"))
            machine=null;
        var payroll=KingdomEnterprisePayrollData.get(server);
        // Preserve accrued debt and never silently erase a legitimate old contract.
        for(var old:new ArrayList<>(payroll.all()))
            if(old.worker().equals(worker.entityId)&&old.active()
                &&!old.enterprise().equals(business.id())) {
                var previous=firms.find(old.enterprise());
                if(previous==null || !previous.owner().equals(player.getUUID())
                    ||!payroll.dismiss(server,player.getUUID(),old.enterprise(),worker.entityId))
                    return new Result(false,"Прежний договор принадлежит другому предприятию. "
                        +"Сначала переведи работника через его владельца.");
            }
        if(!payroll.hire(server,owner,business.id(),worker.entityId,
                Math.max(1,worker.dailyWage)))
            return new Result(false,"Не удалось оформить трудовой договор. Проверь права.");
        worker.employer=owner;
        worker.workplaceId=business.id().toString();
        if(warehousesRequired){
            worker.hasSource=true;worker.sourceX=source.getX();
            worker.sourceY=source.getY();worker.sourceZ=source.getZ();
            worker.hasDestination=true;worker.destX=dest.getX();
            worker.destY=dest.getY();worker.destZ=dest.getZ();
        }else{
            // Switching from a warehouse job must never retain stale personal
            // chests from another company.
            worker.hasSource=false;worker.hasDestination=false;
        }
        if(machine!=null){
            worker.hasWorkstation=true;worker.workX=machine.getX();
            worker.workY=machine.getY();worker.workZ=machine.getZ();
        }else{
            worker.hasWorkstation=false;
        }
        worker.order="work";
        KingdomNpcData.get(server).setDirty();
        if(worker.contractPaused)
            return new Result(false,"Склады и договор назначены, но трудовой договор приостановлен. "
                +"Проверь задолженность в меню предприятия.");
        if(workshop(worker.job)&&machine==null)
            return new Result(false,"Склады и договор настроены. Для профессии «"
                +KingdomProfessionRegistry.title(worker.job)
                +"» поставь подходящий станок TFC или верстак возле ядра предприятия.");
        if("builder".equals(worker.job)
                && KingdomNpcWorkday.site(server,level,worker)==null)
            return new Result(false,"Договор строителя создан. Пока нет действующей стройки "
                +"с настоящим строительным проектом и маркером.");
        if("farmer".equals(worker.job)&&KingdomFarmWorker.site(level,worker)==null)
            return new Result(false,"Склады настроены. Открой ферму → Поля и семена и добавь поле.");
        if("breeder".equals(worker.job)&&KingdomLivestockWork.workSite(level,worker)==null)
            return new Result(false,"Склады настроены. Открой ферму → Загоны и добавь загон.");
        if("lumberjack".equals(worker.job)&&KingdomTfcForestry.workSite(level,worker)==null)
            return new Result(false,"Склады настроены. Открой лесозаготовку → Лесные участки.");
        if(workshop(worker.job) && "minecraft:crafting_table".equals(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(machine).getBlock()).toString())
            && KingdomNpcWorkshopData.get(server).plan(worker.entityId)==null)
            return new Result(false,"Станок и склады назначены. Для работы за верстаком "
                +"один раз задай рецепт в меню NPC → Рецепт.");
        if(!KingdomNpcDuty.hasPhysicalExecutor(worker.job))
            return new Result(false,"Профессия назначена, но физический исполнитель ещё не реализован.");
        return new Result(true,"Готово: "+worker.name+" — "+business.name()
            +(warehousesRequired?" | сырьё "+source.toShortString()
                +" | продукция "+dest.toShortString():" | склады по реальному заданию")
            +(machine==null?"":" | станок "+machine.toShortString())
            +". Приказ «Работать» включён.");
    }
}
