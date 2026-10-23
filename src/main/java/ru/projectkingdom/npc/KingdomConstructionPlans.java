package ru.projectkingdom.npc;

import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/** Immutable plan interface for built-in, frozen and oriented blueprints. */
public final class KingdomConstructionPlans {
    private KingdomConstructionPlans() {}

    private static final String ORIENTED = "oriented:";
    private static final Map<MinecraftServer, Map<String, KingdomDesignEngine.Design>> ROTATED_CACHE =
        new WeakHashMap<>();

    /** Orient without mutating the original file or an already running site. */
    public static String oriented(String source, int quarterTurns) {
        if (source == null || source.isBlank() || source.startsWith(ORIENTED))
            throw new IllegalArgumentException("Invalid source design");
        return ORIENTED + KingdomArchitectGeometry.normalized(quarterTurns) + ":" + source;
    }

    public static KingdomDesignEngine.Design resolve(MinecraftServer server, String key) {
        if (key == null || server == null) return null;
        if (key.startsWith(ORIENTED)) {
            int prefix = ORIENTED.length();
            if (key.length() <= prefix + 2 || key.charAt(prefix + 1) != ':'
                    || key.charAt(prefix) < '0' || key.charAt(prefix) > '3') return null;
            int turn = key.charAt(prefix) - '0';
            String originalKey = key.substring(prefix + 2);
            if (originalKey.startsWith(ORIENTED)) return null;
            synchronized (ROTATED_CACHE) {
                var cache=ROTATED_CACHE.computeIfAbsent(server, unused -> new HashMap<>());
                var existing=cache.get(key);
                if (existing != null) return existing;
                var original=resolve(server,originalKey);
                if (original == null) return null;
                try {
                    var rotated=KingdomArchitectDesignRotation.rotate(original,turn);
                    cache.put(key,rotated);
                    return rotated;
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        if (KingdomSchematicLibrary.isKey(key))
            return KingdomSchematicLibrary.resolve(server, key);
        var builtin = KingdomBuildingTypes.type(key);
        return builtin == null ? null : KingdomBuildingTypes.design(builtin);
    }

    public static String title(String key) {
        if (key == null) return "Неизвестный проект";
        if (key.startsWith(ORIENTED) && key.length() > ORIENTED.length()+2)
            return title(key.substring(ORIENTED.length()+2));
        if (KingdomSchematicLibrary.isKey(key)) return KingdomSchematicLibrary.title(key);
        var kind = KingdomBuildingTypes.type(key);
        return kind == null ? key : kind.title;
    }

    /** One real inventory item per cell, grouped by block rather than state. */
    public static Map<String, Integer> bill(KingdomDesignEngine.Design design) {
        Map<String, Integer> counts = new TreeMap<>();
        for (var cell : design.blocks()) {
            String id = KingdomBlockStateCodec.blockId(cell.material());
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }
}
