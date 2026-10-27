package ru.projectkingdom.npc.voice;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.npc.KingdomNpcData;
import ru.projectkingdom.npc.KingdomNpcMod;

/** Vanilla entity audio is silent; voice2 recordings use the client sound manager directly. */
@Mod.EventBusSubscriber(modid = KingdomNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcVanillaSounds {
    private NpcVanillaSounds() {}

    /** Also migrates existing citizens when their saved entity is loaded. No chunk lookups. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof Villager villager) {
            silenceRegistered(level, villager);
        }
    }

    /** Covers already loaded citizens before their entity data is sent to a new observer. */
    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Villager villager
                && villager.level() instanceof ServerLevel level) {
            silenceRegistered(level, villager);
        }
    }

    private static void silenceRegistered(ServerLevel level, Villager villager) {
        for (var worker : KingdomNpcData.get(level.getServer()).all()) {
            if (worker.entityId.equals(villager.getUUID())) {
                villager.setSilent(true);
                return;
            }
        }
    }
}
