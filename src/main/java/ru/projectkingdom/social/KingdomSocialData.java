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

    public static KingdomSocialData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomSocialData::load, KingdomSocialData::new, KEY);
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
    private static KingdomSocialData load(CompoundTag root) {
        KingdomSocialData data = new KingdomSocialData();
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
        return out;
    }
}
