package ru.projectkingdom.social;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative, persistent personal progression. Not a wallet, office or title registry. */
public final class KingdomSocialData extends SavedData {
    private static final String KEY = "kingdom_social_v1";
    private final Map<UUID, KingdomSocialRank> ranks = new LinkedHashMap<>();
    private boolean legacyMigrationCompleted;

    public static KingdomSocialData get(MinecraftServer server) {
        KingdomSocialData data = server.overworld().getDataStorage().computeIfAbsent(
                KingdomSocialData::load, KingdomSocialData::new, KEY);
        if (!data.legacyMigrationCompleted) data.migrateExistingOwnership(server);
        return data;
    }
    public KingdomSocialRank rank(UUID person) {
        return person == null ? KingdomSocialRank.COMMONER :
                ranks.getOrDefault(person, KingdomSocialRank.COMMONER);
    }
    /** Trusted server-only operation: prerequisite/payment/title verification is external. */
    public boolean grantVerified(UUID person, KingdomSocialRank target) {
        if (person == null || target == null || target.level() <= rank(person).level()) return false;
        ranks.put(person, target);
        setDirty();
        return true;
    }
    /** Use for backfill of old legitimate owners/founders; never downgrades. */
    public boolean preserveAtLeast(UUID person, KingdomSocialRank minimum) {
        return grantVerified(person, minimum);
    }

    /**
     * One-time compatibility backfill for stage-1 worlds without social status data.
     * Economic achievements can justify ranks 2..5, never a noble title or political office.
     * Existing founding records justify rank 6. This neither transfers property nor currency.
     */
    private void migrateExistingOwnership(MinecraftServer server) {
        var firms = new LinkedHashMap<UUID, Long>();
        for (var firm : ru.projectkingdom.npc.KingdomEnterpriseData.get(server).all())
            firms.merge(firm.owner(), 1L, Long::sum);
        var workers = new LinkedHashMap<UUID, Long>();
        for (var worker : ru.projectkingdom.npc.KingdomNpcData.get(server).all())
            if (worker.employer != null) workers.merge(worker.employer, 1L, Long::sum);
        var plots = ru.projectkingdom.npc.KingdomCivicsData.get(server).purchasedPlotOwnerCounts();
        var people = new java.util.LinkedHashSet<UUID>();
        people.addAll(firms.keySet());
        people.addAll(workers.keySet());
        people.addAll(plots.keySet());
        for (UUID person : people) {
            long f = firms.getOrDefault(person, 0L);
            long w = workers.getOrDefault(person, 0L);
            long p = plots.getOrDefault(person, 0L);
            for (int tier = 1; tier <= 5; tier++) {
                var rank = KingdomSocialRank.fromLevel(tier);
                var caps = KingdomSocialPolicy.privileges(rank);
                if (f <= caps.maxEnterprises() && w <= caps.maxPersonalWorkers()
                        && p <= caps.maxPurchasedPlots()) {
                    preserveAtLeast(person, rank);
                    break;
                }
                if (tier == 5) preserveAtLeast(person, KingdomSocialRank.LANDOWNER);
            }
        }
        var administration = ru.projectkingdom.administration.KingdomAdministrationData.get(server);
        for (var town : ru.projectkingdom.npc.KingdomCivicsData.get(server).towns()) {
            UUID founder = administration.founder(town.id());
            if (founder != null) preserveAtLeast(founder, KingdomSocialRank.SETTLEMENT_FOUNDER);
        }
        legacyMigrationCompleted = true;
        setDirty();
    }
    private static KingdomSocialData load(CompoundTag root) {
        KingdomSocialData data = new KingdomSocialData();
        data.legacyMigrationCompleted = root.getBoolean("MigratedV1");
        ListTag rows = root.getList("People", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            if (!row.hasUUID("Person")) continue;
            int level = row.getInt("Level");
            if (level >= 1 && level <= 9)
                data.ranks.put(row.getUUID("Person"), KingdomSocialRank.fromLevel(level));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag out) {
        ListTag rows = new ListTag();
        for (var entry : ranks.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Person", entry.getKey());
            row.putInt("Level", entry.getValue().level());
            rows.add(row);
        }
        out.put("People", rows);
        out.putBoolean("MigratedV1", legacyMigrationCompleted);
        return out;
    }
}
