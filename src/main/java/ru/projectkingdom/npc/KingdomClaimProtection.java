package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side protection for city claims; wilderness is unaffected. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomClaimProtection {
    private KingdomClaimProtection() {}
    private static boolean denied(Player player,BlockPos pos,KingdomCivicsData.Action action) {
        if(!(player instanceof ServerPlayer p)||p.hasPermissions(2))return false;
        ServerLevel level=p.serverLevel();
        KingdomCivicsData data=KingdomCivicsData.get(p.getServer());
        if(data.plot(level,pos)==null)return false;
        return !data.allowed(p.getUUID(),level.dimension().location().toString(),pos.getX(),pos.getZ(),action);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if(denied(event.getPlayer(),event.getPos(),KingdomCivicsData.Action.BREAK))event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(event.getEntity() instanceof Player player && denied(player,event.getPos(),KingdomCivicsData.Action.BUILD))event.setCanceled(true);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(denied(event.getEntity(),event.getPos(),KingdomCivicsData.Action.INTERACT))event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        KingdomCivicsData data=KingdomCivicsData.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos->data.plot(level,pos)!=null);
    }
}
