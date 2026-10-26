package ru.projectkingdom.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomNpcData;
import ru.projectkingdom.npc.KingdomNpcNutrition;
import ru.projectkingdom.npc.KingdomSeasonCalendar;

/**
 * Free residents eat real TFC food at their town's inn, not imaginary rations
 * or employee meals. The tick never loads chunks or consumes bank balances.
 */
public final class KingdomTavernMeals {
    private KingdomTavernMeals(){}
    private static final int MAX_SERVINGS_PER_TICK=2;
    public static void tick(MinecraftServer server){
        if(server==null||server.getTickCount()%1200!=0)return;
        long day=KingdomSeasonCalendar.totalDays(server);
        if(day<0)return;
        var companies=KingdomEnterpriseData.get(server);
        var citizens=KingdomNpcData.get(server);
        var civics=KingdomCivicsData.get(server);
        int served=0;
        for(var firm:companies.all()){
            if(companies.kind(firm.id())!=EnterpriseKind.TAVERN)continue;
            var registered=companies.core(firm.id());
            if(registered==null||!registered.active())continue;
            ServerLevel level=null;
            for(ServerLevel candidate:server.getAllLevels())if(candidate.dimension().location().toString()
                    .equals(registered.dimension())){level=candidate;break;}
            if(level==null)continue;
            BlockPos tavern=BlockPos.of(registered.packedPos());
            if(!level.hasChunkAt(tavern))continue;
            var town=civics.townAt(level,tavern);
            if(town==null||!civics.isMember(town.id(),firm.owner()))continue;
            if(KingdomTavernImmigration.inspect(server,firm.id()).beds()==0)continue;
            for(var worker:citizens.all()){
                if(!town.id().toString().equals(worker.settlement)
                        ||worker.employer!=null||!"free".equals(worker.job)
                        ||worker.lastMealDay>=day)continue;
                // Arrive at the tavern to be fed; no remote invisible feeding.
                var entity=level.getEntity(worker.entityId);
                if(!(entity instanceof Villager npc)||!npc.isAlive()
                        ||npc.distanceToSqr(tavern.getX()+.5,tavern.getY()+.5,tavern.getZ()+.5)>256)
                    continue;
                var food=KingdomTavernProvisions.choose(level,firm.id());
                if(food==null||!KingdomTavernProvisions.consume(food))continue;
                if(food.nutrition()!=null)KingdomNpcNutrition.eat(worker,food.nutrition());
                else worker.hunger=Math.min(100,worker.hunger+20); // vanilla edible fallback
                worker.lastMealDay=day;
                citizens.setDirty();
                if(++served>=MAX_SERVINGS_PER_TICK)return;
            }
        }
    }
}
