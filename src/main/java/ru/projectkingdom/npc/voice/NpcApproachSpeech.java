package ru.projectkingdom.npc.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import ru.projectkingdom.npc.KingdomNpcData;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.npc.NpcDialogueEngine;
import ru.projectkingdom.npc.NpcDialogueService;

/** Server-selected encounter dialogue; visits loaded citizens only once per second. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcApproachSpeech {
    private static final NpcApproachGate GATE=new NpcApproachGate();
    private NpcApproachSpeech() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        var server=ServerLifecycleHooks.getCurrentServer();
        if (server==null || server.getTickCount()%20!=0) return;
        long now=server.overworld().getGameTime();
        var encounters=new ArrayList<NpcApproachGate.Encounter>();
        var citizens=new HashMap<UUID,KingdomNpcData.Worker>();
        var entities=new HashMap<UUID,Villager>();
        for (var worker:KingdomNpcData.get(server).all()) {
            var dimension=ResourceLocation.tryParse(worker.dimension);
            if (dimension==null) continue;
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));
            if (level==null || !(level.getEntity(worker.entityId) instanceof Villager npc)
                    || !npc.isAlive()) continue;
            // Restores the invariant for already-loaded older citizens or external Silent resets.
            if (!npc.isSilent()) npc.setSilent(true);
            citizens.put(worker.entityId,worker);entities.put(worker.entityId,npc);
            List<UUID> listeners=level.players().stream().filter(p -> p.distanceToSqr(npc)<=256)
                    .map(ServerPlayer::getUUID).toList();
            for (var player:level.players()) {
                double distance=player.distanceToSqr(npc);
                if (distance>64) continue;
                encounters.add(new NpcApproachGate.Encounter(worker.entityId,player.getUUID(),distance,
                        distance<=25 && player.hasLineOfSight(npc),
                        !npc.isSleeping() && player.isAlive() && !player.isSpectator() && !player.isSleeping()
                                && player.containerMenu==player.inventoryMenu,listeners));
            }
        }
        for (var encounter:GATE.select(now,encounters)) {
            var worker=citizens.get(encounter.npcId());
            var npc=entities.get(encounter.npcId());
            var state=new NpcDialogueEngine.Snapshot(worker.name,worker.job,worker.hunger,worker.energy,
                    worker.morale,worker.safety,worker.employer!=null,worker.contractPaused,
                    worker.hasHome,worker.hasWorkstation,worker.order,0);
            int variation=worker.entityId.hashCode()+(int)(now/NpcApproachGate.NPC_PAUSE);
            NpcDialogueService.announce(npc,worker,NpcApproachLines.choose(state,variation));
        }
    }

    /** All explicit replies remain immediate, while automatic speech waits for quiet. */
    public static void spoken(ServerLevel level,UUID npc,List<UUID> listeners) {
        GATE.spoken(level.getServer().overworld().getGameTime(),npc,listeners);
    }

    public static void greetOnInteraction(ServerPlayer player,KingdomNpcData.Worker worker) {
        long now=player.getServer().overworld().getGameTime();
        if (!GATE.recentlySpoke(now,worker.entityId,200))
            NpcDialogueService.respond(player,worker,"привет");
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { GATE.clear(); }
}
