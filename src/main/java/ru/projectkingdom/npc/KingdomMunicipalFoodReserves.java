package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.AdministrativeCoreBinding;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;

/**
 * Stage 2: municipal food is physically owned and stored in CENTRAL_WAREHOUSE
 * containers. The reserve forecast creates an INFORMATIONAL deficit notice;
 * only a separately authorized physical trade can spend coins or move goods.
 */
public final class KingdomMunicipalFoodReserves extends SavedData {
    private static final String KEY = "kingdom_city_food_reserves_v1";
    private static final AdministrativeBindingRole STORAGE =
            AdministrativeBindingRole.WAREHOUSE_STORAGE;
    public record Policy(int residents,int portionsPerDay,long firstWinterDay,int yearDays,
                         int winterDays,int preparationDays,int safetyDays) {
        public Policy {
            if(residents < 1 || residents > 100000 || portionsPerDay < 1 || portionsPerDay > 20
                    || firstWinterDay < -yearDays || yearDays < 8 || yearDays > 10000
                    || winterDays < 1 || winterDays >= yearDays
                    || preparationDays < 0 || preparationDays > yearDays
                    || safetyDays < 0 || safetyDays > yearDays)
                throw new IllegalArgumentException("Invalid city food policy");
        }
        /**
         * TFC calendar begins in January. Winter spans December-February,
         * so its previous-year start is month -1 at calendar epoch 0.
         * Repeating cycle follows the actual configured month length.
         */
        public static Policy fromTfcMonth(int residents,int monthDays) {
            if(monthDays<1 || monthDays>833)throw new IllegalArgumentException("TFC month length");
            return new Policy(residents,2,-monthDays,monthDays*12,
                    monthDays*3,monthDays*3,monthDays);
        }

        public static Policy defaults(int residents,long firstWinterDay,int yearDays) {
            int winter=Math.max(1,yearDays/4);
            return new Policy(residents,2,firstWinterDay,yearDays,winter,
                    Math.min(winter,yearDays),Math.min(8,yearDays));
        }
    }
    /** Immutable server-side snapshot; each group covers exactly one category. */
    public record Forecast(long day,boolean complete,int containers,int edible,int rotten,
                           List<Integer> groups,int daysToCover,long target,int missing) {
        public Forecast {
            groups=List.copyOf(groups);
            if(groups.size()!=KingdomNpcNutrition.COUNT || containers<0
                    || edible<0 || rotten<0 || daysToCover<0 || target<0 || missing<0)
                throw new IllegalArgumentException("Invalid food forecast");
        }
        public boolean shortage(){return complete && missing>0;}
    }

    private final Map<UUID,Policy> policies = new LinkedHashMap<>();
    private final Map<UUID,Forecast> forecasts = new LinkedHashMap<>();
    private int cursor;

    public static KingdomMunicipalFoodReserves get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomMunicipalFoodReserves::load,KingdomMunicipalFoodReserves::new,KEY);
    }
    public Policy policy(UUID town){return policies.get(town);}
    public Forecast forecast(UUID town){return forecasts.get(town);}
    public boolean configure(UUID town,Policy policy) {
        if(town==null || policy==null)return false;
        if(!policy.equals(policies.put(town,policy)))setDirty();
        forecasts.remove(town);setDirty();
        return true;
    }

    /**
     * A winter cycle is explicitly anchored to TFC calendar days. We do NOT
     * infer season length from Minecraft dayTime, because TFC configurations
     * may change the year length.
     *
     * Outside the preparation period we only maintain a rolling safety buffer.
     */
    public static int coverageDays(Policy p,long calendarDay) {
        if(p==null || calendarDay<0)return 0;
        long first=p.firstWinterDay();
        long start=first;
        if(calendarDay>=first) {
            long cycles=(calendarDay-first)/p.yearDays();
            start=first+cycles*p.yearDays();
            if(calendarDay>=start+p.winterDays())start+=p.yearDays();
        }
        long horizon;
        if(calendarDay<start) {
            long until=start-calendarDay;
            horizon=until<=p.preparationDays()
                    ?until+p.winterDays()+p.safetyDays()
                    :p.safetyDays();
        } else horizon=start+p.winterDays()-calendarDay+p.safetyDays();
        return (int)Math.min(Integer.MAX_VALUE,Math.max(0,horizon));
    }

    public static long target(Policy p,int coverageDays) {
        if(p==null || coverageDays<0)throw new IllegalArgumentException("coverage");
        return Math.multiplyExact(Math.multiplyExact((long)p.residents(),p.portionsPerDay()),
                (long)coverageDays);
    }
    public static int shortage(long target,int edible) {
        if(target<0 || edible<0)throw new IllegalArgumentException("stock");
        return (int)Math.min(Integer.MAX_VALUE,Math.max(0L,target-edible));
    }

    /** Inspect every registered city-owned container. Any missing chest is UNKNOWN. */
    public Forecast inspect(MinecraftServer server,UUID town) {
        Policy policy=policies.get(town);
        long day=KingdomSeasonCalendar.totalDays(server);
        if(policy==null)return null;
        int coverage=coverageDays(policy,day);
        long target=target(policy,coverage);
        int[] groups=new int[KingdomNpcNutrition.COUNT];
        int counted=0;
        long edible=0,rotten=0;
        // A changed TFC month-length setting invalidates the persisted
        // seasonal policy until the mayor reconfigures it.
        int monthDays=KingdomSeasonCalendar.daysInMonth(server);
        boolean complete=day>=0 && monthDays>0
                && monthDays*12==policy.yearDays()
                && KingdomNpcNutrition.available();
        var storage=KingdomMunicipalFoodStorage.inspect(server,town);
        complete &= storage.complete();
        for(var bound:storage.containers()) {
            Container container=bound.container();
            counted++;
            for(int slot=0;slot<container.getContainerSize();slot++) {
                ItemStack stack=container.getItem(slot);
                if(stack.isEmpty())continue;
                if(KingdomNpcNutrition.spoiled(stack)) {
                    rotten+=stack.getCount();continue;
                }
                var meal=KingdomNpcNutrition.inspect(stack);
                if(meal==null)continue;
                edible+=stack.getCount();
                int[] nutrients=meal.nutrients();
                // Mixed TFC meals (e.g. soup) support EVERY real group they contain.
                // One edible ItemStack remains one portion for the total forecast,
                // but it can count toward multiple nutritional group coverage targets.
                for(int g=0;g<Math.min(nutrients.length,groups.length);g++)
                    if(nutrients[g]>0)
                        groups[g]=(int)Math.min(Integer.MAX_VALUE,
                            (long)groups[g]+stack.getCount());
            }
        }
        int available=(int)Math.min(Integer.MAX_VALUE,edible);
        return new Forecast(day,complete,counted,available,
                (int)Math.min(Integer.MAX_VALUE,rotten),
                Arrays.stream(groups).boxed().toList(),coverage,target,
                complete?shortage(target,available):0);
    }

    public Forecast evaluate(MinecraftServer server,UUID town) {
        Forecast current=inspect(server,town);
        if(current==null)return null;
        Forecast previous=forecasts.put(town,current);
        if(!current.equals(previous))setDirty();
        return current;
    }
    /**
     * Evaluate at most two enabled towns each minute, rather than scanning
     * every municipal chest every tick. Never force chunks.
     */
    public void tick(MinecraftServer server) {
        if(policies.isEmpty())return;
        List<UUID> towns=new ArrayList<>(policies.keySet());
        int limit=Math.min(2,towns.size());
        for(int i=0;i<limit;i++) {
            UUID town=towns.get(cursor++%towns.size());
            if(KingdomCivicsData.get(server).townById(town)!=null)evaluate(server,town);
        }
        if(cursor>1000000)cursor%=towns.size();
    }

    static KingdomMunicipalFoodReserves load(CompoundTag root) {
        var result=new KingdomMunicipalFoodReserves();
        for(Tag raw:root.getList("Policies",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)raw;
            if(!t.hasUUID("Town"))continue;
            try{
                var p=new Policy(t.getInt("Residents"),t.getInt("Portions"),
                        t.getLong("WinterStart"),t.getInt("YearDays"),
                        t.getInt("WinterDays"),t.getInt("PreparationDays"),t.getInt("SafetyDays"));
                result.policies.put(t.getUUID("Town"),p);
            }catch(IllegalArgumentException ignored){}
        }
        // Forecasts are derived observations. After a restart they must
        // be revalidated against real chests, never trusted as current stock.
        return result;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag();
        for(var entry:policies.entrySet()) {
            CompoundTag t=new CompoundTag();
            Policy p=entry.getValue();t.putUUID("Town",entry.getKey());
            t.putInt("Residents",p.residents());t.putInt("Portions",p.portionsPerDay());
            t.putLong("WinterStart",p.firstWinterDay());t.putInt("YearDays",p.yearDays());
            t.putInt("WinterDays",p.winterDays());
            t.putInt("PreparationDays",p.preparationDays());t.putInt("SafetyDays",p.safetyDays());
            list.add(t);
        }
        root.put("Policies",list);
        return root;
    }
}
