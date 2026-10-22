package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable, server-authoritative map geometry shared by future map adapters. */
public final class KingdomMapSnapshot {
    private KingdomMapSnapshot() {}
    public record Claim(int chunkX, int chunkZ, int minX, int minZ, int maxX, int maxZ,
                        UUID townId, String townName, KingdomCivicsData.Zone zone, UUID tenant) {}

    public static List<Claim> from(KingdomCivicsData data, String dimension) {
        List<Claim> result = new ArrayList<>();
        for (KingdomCivicsData.Plot plot : data.mapPlots(dimension)) {
            KingdomCivicsData.Town town = data.townById(plot.town());
            if (town == null) continue;
            int x = plot.x() << 4, z = plot.z() << 4;
            result.add(new Claim(plot.x(), plot.z(), x, z, x + 15, z + 15,
                    town.id(), town.name(), plot.zone(), plot.tenant()));
        }
        return List.copyOf(result);
    }
}
