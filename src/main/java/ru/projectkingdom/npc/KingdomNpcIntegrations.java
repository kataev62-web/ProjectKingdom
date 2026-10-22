package ru.projectkingdom.npc;

import net.minecraftforge.fml.ModList;
import java.util.ArrayList;
import java.util.List;

/** Optional mod discovery. No classes from external mods are loaded here. */
public final class KingdomNpcIntegrations {
    private KingdomNpcIntegrations() {}
    public static boolean installed(String id) { return ModList.get().isLoaded(id); }
    public static String report() {
        List<String> result = new ArrayList<>();
        for (String id : List.of("tfc", "minecolonies", "create", "smallships", "astikorcarts"))
            result.add(id + "=" + (installed(id) ? "detected" : "absent"));
        return String.join(", ", result);
    }
    public interface WorkAdapter {
        String modId();
        boolean canHandle(String workplaceKind);
        /** Implementations must validate loaded chunks, permissions and inventory before mutation. */
        boolean tryPerform(String workplaceId, int workerId);
    }
}
