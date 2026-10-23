package ru.projectkingdom.npc;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.MinecraftServer;

/** One immutable plan interface for built-in templates and frozen .schem projects. */
public final class KingdomConstructionPlans {
    private KingdomConstructionPlans() {}

    public static KingdomDesignEngine.Design resolve(MinecraftServer server, String key) {
        if (key == null) return null;
        if (KingdomSchematicLibrary.isKey(key))
            return KingdomSchematicLibrary.resolve(server, key);
        var builtin = KingdomBuildingTypes.type(key);
        return builtin == null ? null : KingdomBuildingTypes.design(builtin);
    }

    public static String title(String key) {
        if (key == null) return "Неизвестный проект";
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
