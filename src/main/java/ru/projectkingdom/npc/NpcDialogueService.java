package ru.projectkingdom.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.UUID;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.NpcSpeechPacket;
import ru.projectkingdom.npc.voice.NpcVoiceLine;
import ru.projectkingdom.npc.voice.NpcVoiceBank;
import ru.projectkingdom.npc.voice.NpcVoiceRecordings;
import ru.projectkingdom.npc.voice.NpcApproachSpeech;
import ru.projectkingdom.npc.voice.NpcSpeechText;

/** Forge-facing conversation boundary. All material effects are validated on the server. */
public final class NpcDialogueService {
    static final double CONVERSATION_RANGE_SQUARED = 64.0;
    static final double LISTENER_RANGE_SQUARED = 256.0;

    private NpcDialogueService() {}

    public static boolean respondAddressed(ServerPlayer player, String rawText) {
        KingdomNpcData data = KingdomNpcData.get(player.getServer());
        var candidates = new ArrayList<NpcAddressing.Candidate>();
        for (KingdomNpcData.Worker worker : data.all()) {
            boolean sameDimension = worker.dimension.equals(player.level().dimension().location().toString());
            Villager npc = sameDimension ? loadedNpc(player.serverLevel(), worker) : null;
            double distance = npc == null ? Double.MAX_VALUE : player.distanceToSqr(npc);
            candidates.add(new NpcAddressing.Candidate(worker.id, worker.name, distance,
                    npc != null && npc.isAlive() && distance <= CONVERSATION_RANGE_SQUARED));
        }
        var match = NpcAddressing.match(rawText, candidates);
        if (match.isEmpty()) return false;
        KingdomNpcData.Worker worker = data.workers.get(match.get().npcId());
        return respond(player, worker, match.get().phrase());
    }

    /** Voice may target the looked-at NPC; the server resolves and revalidates that UUID. */
    public static boolean respondVoice(ServerPlayer player, UUID targetEntity, String text) {
        if(player==null||text==null||text.isBlank())return false;
        if(targetEntity==null)return respondAddressed(player,text);
        KingdomNpcData.Worker worker=null;
        for(var candidate:KingdomNpcData.get(player.getServer()).all())
            if(candidate.entityId.equals(targetEntity)){worker=candidate;break;}
        return respond(player,worker,text);
    }

    public static boolean respond(ServerPlayer player, KingdomNpcData.Worker w, String utterance) {
        if (player == null || w == null || utterance == null || utterance.isBlank()) return false;
        if (!w.dimension.equals(player.level().dimension().location().toString())) return false;
        Villager npc = loadedNpc(player.serverLevel(), w);
        if (npc == null || !npc.isAlive() || player.distanceToSqr(npc) > CONVERSATION_RANGE_SQUARED) {
            player.sendSystemMessage(Component.literal("[Королевство] Подойди к NPC не дальше чем на 8 блоков."));
            return false;
        }

        NpcSocialMemory before = w.social(player.getUUID());
        var snapshot = new NpcDialogueEngine.Snapshot(w.name, w.job, w.hunger, w.energy, w.morale,
                w.safety, w.employer != null, w.contractPaused, w.hasHome, w.hasWorkstation,
                w.order, before.reputation());
        NpcDialogueEngine.Reply reply = NpcDialogueEngine.reply(utterance, snapshot);
        long day = player.serverLevel().getDayTime() / 24000L;
        NpcSocialMemory memory = before.talked(reply.intent().name(), day);
        String text = reply.text();
        NpcVoiceLine line = reply.line();

        if (reply.action() == NpcDialogueEngine.Action.GIVE_BREAD) {
            if (consumeBread(player)) {
                w.hunger = NpcLife.feed(w.hunger, 1);
                memory = memory.helped(day);
                text = "Спасибо за хлеб. Я запомню твою помощь.";
                line = NpcVoiceLine.BREAD_THANKS;
            } else {
                text = "У тебя нет настоящего хлеба. Сначала принеси его.";
                line = NpcVoiceLine.BREAD_MISSING;
            }
        }

        w.remember(player.getUUID(), memory);
        KingdomNpcData.get(player.getServer()).setDirty();
        speak(player.serverLevel(), npc, w, text, line,false);

        if (reply.action() == NpcDialogueEngine.Action.OPEN_MANAGEMENT) {
            KingdomNpcMod.openNpcManagement(player, w.id);
        } else if (reply.action() == NpcDialogueEngine.Action.OPEN_TRADE) {
            openTradeOrExplain(player,npc,w);
        }
        return true;
    }

    private static void openTradeOrExplain(ServerPlayer player, Villager npc, KingdomNpcData.Worker w) {
        if (!"merchant".equals(w.job) && !"warehouse".equals(w.job)) return;
        var market=KingdomWarehouseMarket.get(player.getServer());
        var playerTown=market.nearbyCityMarketTown(player.serverLevel(),player.blockPosition(),8);
        var npcTown=market.nearbyCityMarketTown(player.serverLevel(),npc.blockPosition(),8);
        if(playerTown==null || !playerTown.equals(npcTown)) {
            player.sendSystemMessage(Component.literal(
                    "[Королевство] Для торговли вы с NPC должны находиться у одного активного городского рынка."));
            return;
        }
        KingdomMainMenu.openWarehouse(player);
    }

    private static Villager loadedNpc(ServerLevel level, KingdomNpcData.Worker worker) {
        var entity = level.getEntity(worker.entityId);
        return entity instanceof Villager villager ? villager : null;
    }

    private static boolean consumeBread(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.BREAD) && !stack.isEmpty()) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    /** Automatic speech carries no gameplay actions or social-memory changes. */
    public static void announce(Villager npc,KingdomNpcData.Worker worker,NpcVoiceLine line) {
        if (npc.isAlive() && npc.level() instanceof ServerLevel level)
            speak(level,npc,worker,"",line,true);
    }

    private static void speak(ServerLevel level, Villager npc, KingdomNpcData.Worker worker,
                              String details, NpcVoiceLine line,boolean automatic) {
        if (!npc.isSilent()) npc.setSilent(true);
        var voice=NpcVoiceBank.forNpc(worker.entityId,worker.gender);
        var speech=new NpcSpeechPacket(npc.getUUID(),NpcSpeechText.limit(worker.name,64,"Житель"),
                NpcSpeechText.limit(details,512,""),NpcSpeechText.limit(worker.gender,16,"unspecified"),
                NpcVoiceRecordings.durationTicks(voice,line),voice,line,automatic);
        var listeners=new ArrayList<UUID>();
        for (ServerPlayer listener : level.players()) {
            if (listener.distanceToSqr(npc) <= LISTENER_RANGE_SQUARED) {
                KingdomNetwork.sendNpcSpeech(listener,speech);
                listeners.add(listener.getUUID());
            }
        }
        NpcApproachSpeech.spoken(level,npc.getUUID(),listeners);
    }
}

