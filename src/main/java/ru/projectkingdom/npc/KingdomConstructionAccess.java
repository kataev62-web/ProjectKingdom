package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Single server-side building authorization shared by placement and NPC construction.
 *  New civics titles take precedence; legacy-only claims remain readable for older worlds.
 */
public final class KingdomConstructionAccess {
    private KingdomConstructionAccess() {}

    public static boolean canBuild(MinecraftServer server, ServerLevel level, BlockPos pos, UUID actor) {
        if (server == null || level == null || pos == null || actor == null) return false;
        KingdomCivicsData civics = KingdomCivicsData.get(server);
        if (civics.plot(level, pos) != null) {
            return civics.allowed(actor, level.dimension().location().toString(), pos.getX(), pos.getZ(),
                    KingdomCivicsData.Action.BUILD);
        }
        // Compatibility for worlds with old claims that have not been migrated to civics.
        return KingdomTerritories.get(server).canUse(level, pos, actor);
    }
}
