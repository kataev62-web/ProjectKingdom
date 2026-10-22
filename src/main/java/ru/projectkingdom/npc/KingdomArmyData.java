package ru.projectkingdom.npc;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent army order registry; physical soldiers/AI are a separate integration task. */
public final class KingdomArmyData extends SavedData {
    public enum Order { HOLD, FOLLOW, PATROL, DEFEND, ATTACK }
    public record Unit(UUID id,UUID owner,String name,Order order) {}
    private final LinkedHashMap<UUID,Unit> units=new LinkedHashMap<>();
    public static KingdomArmyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomArmyData::load,KingdomArmyData::new,"kingdom_army_v1");
    }
    public Collection<Unit> all(){return Collections.unmodifiableCollection(units.values());}
    public Unit create(UUID owner,String name) {
        Unit u=new Unit(UUID.randomUUID(),owner,name,Order.HOLD);units.put(u.id(),u);setDirty();return u;
    }
    public boolean order(UUID id,UUID owner,Order order) {
        Unit u=units.get(id);if(u==null||!u.owner().equals(owner))return false;
        units.put(id,new Unit(u.id(),u.owner(),u.name(),order));setDirty();return true;
    }
    private static KingdomArmyData load(CompoundTag tag) {
        KingdomArmyData d=new KingdomArmyData();ListTag rows=tag.getList("Units",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++) {
            CompoundTag r=rows.getCompound(i);
            try {Unit u=new Unit(r.getUUID("Id"),r.getUUID("Owner"),r.getString("Name"),Order.valueOf(r.getString("Order")));d.units.put(u.id(),u);}catch(IllegalArgumentException ignored){}
        }return d;
    }
    @Override public CompoundTag save(CompoundTag out) {
        ListTag rows=new ListTag();for(Unit u:units.values()) {
            CompoundTag r=new CompoundTag();r.putUUID("Id",u.id());r.putUUID("Owner",u.owner());r.putString("Name",u.name());r.putString("Order",u.order().name());rows.add(r);
        }out.put("Units",rows);return out;
    }
}
