package ru.projectkingdom.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable one-citizen-per-town arrival throttle, independent of loaded chunks. */
public final class KingdomTavernArrivals extends SavedData {
    private static final String KEY="kingdom_tavern_arrivals_v1";
    private final Map<UUID,Long> last=new LinkedHashMap<>();
    public static KingdomTavernArrivals get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomTavernArrivals::load,KingdomTavernArrivals::new,KEY);
    }
    public boolean ready(UUID town,long ticks){
        if(town==null||ticks<0)return false;
        Long prev=last.get(town);
        return prev==null||ticks>=prev&&ticks-prev>=KingdomTavernImmigration.ARRIVAL_INTERVAL_TICKS;
    }
    public void record(UUID town,long ticks){
        if(town!=null&&ticks>=0){last.put(town,ticks);setDirty();}
    }
    public long last(UUID town){return last.getOrDefault(town,-1L);}
    public static KingdomTavernArrivals load(CompoundTag root){
        var loaded=new KingdomTavernArrivals();
        ListTag list=root.getList("Arrivals",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size()&&i<10000;i++){
            var item=list.getCompound(i);
            if(item.hasUUID("Town")&&item.getLong("Tick")>=0)
                loaded.last.put(item.getUUID("Town"),item.getLong("Tick"));
        }
        return loaded;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();
        for(var entry:last.entrySet()){
            var item=new CompoundTag();
            item.putUUID("Town",entry.getKey());item.putLong("Tick",entry.getValue());list.add(item);
        }
        root.put("Arrivals",list);return root;
    }
}
