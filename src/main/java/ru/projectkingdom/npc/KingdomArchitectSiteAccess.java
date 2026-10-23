package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.enterprise.KingdomEnterpriseData;
import ru.projectkingdom.enterprise.KingdomEnterpriseRegistry;

/**
 * A company construction core can replace the old drafting lectern,
 * but construction company authority is still enforced by the server.
 */
public final class KingdomArchitectSiteAccess {
    private KingdomArchitectSiteAccess() {}

    public static boolean mayUse(ServerPlayer player, BlockPos marker) {
        if (player == null || marker == null || !player.serverLevel().hasChunkAt(marker))
            return false;
        if (!KingdomConstructionMarkers.isMarker(player.serverLevel(),marker))
            return false;
        if (!player.serverLevel().getBlockState(marker)
                .is(KingdomEnterpriseRegistry.CONSTRUCTION.get()))
            return true; // Old drafting table and lectern follow existing claim rules.
        if (!(player.serverLevel().getBlockEntity(marker) instanceof EnterpriseCoreBlockEntity core)
                || !core.bound() || core.kind()!=EnterpriseKind.CONSTRUCTION)
            return false;
        return KingdomEnterpriseData.get(player.getServer())
            .mayManage(player.getUUID(),core.enterpriseId());
    }
}
