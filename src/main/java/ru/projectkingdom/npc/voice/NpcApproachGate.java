package ru.projectkingdom.npc.voice;
import java.util.*;

/** Encounter hysteresis and audience-wide quiet periods; no Minecraft dependencies. */
public final class NpcApproachGate {
    public static final int NPC_PAUSE = 1200, LISTENER_PAUSE = 240;
    public record Encounter(UUID npcId, UUID playerId, double distanceSquared, boolean visible, boolean available,
                            List<UUID> listeners) {
        public Encounter { listeners = List.copyOf(listeners); }
        public Encounter(UUID npcId,UUID playerId,double distanceSquared,boolean visible,List<UUID> listeners) {
            this(npcId,playerId,distanceSquared,visible,true,listeners);
        }
    }
    private record Pair(UUID npc, UUID player) {}
    private final Set<Pair> inside = new HashSet<>();
    private final Map<UUID,Long> npcUntil = new HashMap<>(), listenerUntil = new HashMap<>();
    private long clock = Long.MIN_VALUE;

    public List<Encounter> select(long now,List<Encounter> encounters) {
        advance(now);
        Set<Pair> present = new HashSet<>();
        List<Encounter> fresh = new ArrayList<>();
        for (var e : encounters) {
            if (!Double.isFinite(e.distanceSquared()) || e.distanceSquared()<0 || e.distanceSquared()>64) continue;
            var pair = new Pair(e.npcId(),e.playerId());
            present.add(pair);
            if (e.distanceSquared()<=25 && e.visible() && inside.add(pair) && e.available()) fresh.add(e);
        }
        inside.retainAll(present);
        fresh.sort(Comparator.comparingDouble(Encounter::distanceSquared)
                .thenComparing(Encounter::npcId).thenComparing(Encounter::playerId));
        List<Encounter> chosen = new ArrayList<>();
        for (var e : fresh) {
            if (npcUntil.getOrDefault(e.npcId(),Long.MIN_VALUE)>now
                    || listenerUntil.getOrDefault(e.playerId(),Long.MIN_VALUE)>now
                    || e.listeners().stream().anyMatch(id -> listenerUntil.getOrDefault(id,Long.MIN_VALUE)>now)) continue;
            chosen.add(e);
            spoken(now,e.npcId(),e.listeners());
            listenerUntil.put(e.playerId(),now+LISTENER_PAUSE);
        }
        return List.copyOf(chosen);
    }
    public void spoken(long now,UUID npc,List<UUID> listeners) {
        advance(now);
        npcUntil.put(npc,now+NPC_PAUSE);
        for (var id : listeners) listenerUntil.put(id,now+LISTENER_PAUSE);
    }
    public boolean recentlySpoke(long now,UUID npc,int ticks) {
        advance(now);
        Long until=npcUntil.get(npc);
        return until!=null && now-until+NPC_PAUSE<ticks;
    }
    private void advance(long now) {
        if (now<clock) clear();
        clock=now;
        npcUntil.values().removeIf(until -> until<=now);
        listenerUntil.values().removeIf(until -> until<=now);
    }
    public void clear() {
        inside.clear();npcUntil.clear();listenerUntil.clear();clock=Long.MIN_VALUE;
    }
}
