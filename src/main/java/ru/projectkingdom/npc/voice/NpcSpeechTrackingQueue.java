package ru.projectkingdom.npc.voice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Client-thread queue retaining speech briefly until the NPC spawn packet arrives. */
public final class NpcSpeechTrackingQueue<T> {
    private record Pending<T>(T value, long expiresAt) {}
    private final LinkedHashMap<UUID, Pending<T>> pending = new LinkedHashMap<>();
    private final int maximum;
    private final int waitTicks;

    public NpcSpeechTrackingQueue(int maximum, int waitTicks) {
        if (maximum < 1 || waitTicks < 1) throw new IllegalArgumentException("positive limits required");
        this.maximum = maximum;
        this.waitTicks = waitTicks;
    }

    public void offer(UUID npc, T value, long now) {
        pending.remove(npc);
        if (pending.size() >= maximum) pending.remove(pending.keySet().iterator().next());
        pending.put(npc, new Pending<>(value, now + waitTicks));
    }

    public List<T> takeReady(long now, Predicate<UUID> tracked, Consumer<T> expired) {
        var ready = new ArrayList<T>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now >= entry.getValue().expiresAt()) {
                iterator.remove();
                expired.accept(entry.getValue().value());
            } else if (tracked.test(entry.getKey())) {
                iterator.remove();
                ready.add(entry.getValue().value());
            }
        }
        return List.copyOf(ready);
    }

    public boolean anyMatch(Predicate<T> predicate) {
        return pending.values().stream().anyMatch(entry -> predicate.test(entry.value()));
    }

    public void removeIf(Predicate<T> predicate) {
        pending.values().removeIf(entry -> predicate.test(entry.value()));
    }

    public void remove(UUID npc) { pending.remove(npc); }
    public boolean isEmpty() { return pending.isEmpty(); }
    public void clear() { pending.clear(); }
}
