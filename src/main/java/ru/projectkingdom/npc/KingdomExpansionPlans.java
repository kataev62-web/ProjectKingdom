package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent administrative expansion proposals; never creates ownership or money. */
public final class KingdomExpansionPlans extends SavedData {
    private static final String KEY = "kingdom_city_expansion_plans_v1";
    public record Cell(String dimension, int x, int z) {}
    private final Map<UUID, LinkedHashSet<Cell>> plans = new HashMap<>();

    public static KingdomExpansionPlans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomExpansionPlans::load, KingdomExpansionPlans::new, KEY);
    }
    public Set<Cell> snapshot(UUID city) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(plans.getOrDefault(city, new LinkedHashSet<>())));
    }
    public void replace(UUID city, Collection<Cell> cells) {
        Objects.requireNonNull(city);
        if (cells.size() > 256) throw new IllegalArgumentException("Plan exceeds 256 chunks");
        LinkedHashSet<Cell> copy = new LinkedHashSet<>(cells);
        if (copy.size() > 256) throw new IllegalArgumentException("Plan exceeds 256 chunks");
        if (copy.isEmpty()) plans.remove(city); else plans.put(city, copy);
        setDirty();
    }
    public static KingdomExpansionPlans load(CompoundTag root) {
        var data = new KingdomExpansionPlans();
        ListTag cities = root.getList("Cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < cities.size(); i++) {
            CompoundTag entry = cities.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            UUID id = entry.getUUID("Id");
            ListTag cells = entry.getList("Cells", Tag.TAG_COMPOUND);
            LinkedHashSet<Cell> set = new LinkedHashSet<>();
            for (int j = 0; j < cells.size() && j < 256; j++) {
                CompoundTag row = cells.getCompound(j);
                set.add(new Cell(row.getString("Dim"), row.getInt("X"), row.getInt("Z")));
            }
            if (!set.isEmpty()) data.plans.put(id, set);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag cities = new ListTag();
        for (var entry : plans.entrySet()) {
            CompoundTag city = new CompoundTag();
            city.putUUID("Id", entry.getKey());
            ListTag cells = new ListTag();
            for (Cell cell : entry.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putString("Dim", cell.dimension());
                row.putInt("X", cell.x());
                row.putInt("Z", cell.z());
                cells.add(row);
            }
            city.put("Cells", cells); cities.add(city);
        }
        root.put("Cities", cities);
        return root;
    }
}
