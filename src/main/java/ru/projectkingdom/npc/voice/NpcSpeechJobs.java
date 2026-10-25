package ru.projectkingdom.npc.voice;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded latest-only synthesis inbox. A single client worker polls it. */
public final class NpcSpeechJobs {
    public record Job(UUID entityId, String text, String gender, long expiresAt) {}
    private final int capacity;
    private final LongSupplier clock;
    private final LinkedHashMap<UUID, Job> latest = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Job> pending = new LinkedHashMap<>();

    public NpcSpeechJobs(int capacity) { this(capacity,System::nanoTime); }

    NpcSpeechJobs(int capacity,LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.clock = clock;
    }

    public synchronized Job offer(UUID entityId, String text, String gender) {
        return offer(entityId,text,gender,160);
    }

    public synchronized Job offer(UUID entityId,String text,String gender,int durationTicks) {
        if(durationTicks<100||durationTicks>160)throw new IllegalArgumentException("Invalid duration");
        var job = new Job(Objects.requireNonNull(entityId),Objects.requireNonNull(text),gender,
                clock.getAsLong()+durationTicks*50_000_000L);
        latest.remove(entityId);
        pending.remove(entityId);
        latest.put(entityId, job);
        pending.put(entityId, job);
        while (latest.size() > capacity) {
            UUID oldest = latest.keySet().iterator().next();
            latest.remove(oldest);
            pending.remove(oldest);
        }
        return job;
    }

    public synchronized Job poll() {
        pending.values().removeIf(job->clock.getAsLong()-job.expiresAt()>=0);
        if (pending.isEmpty()) return null;
        UUID first = pending.keySet().iterator().next();
        return pending.remove(first);
    }

    public synchronized boolean isCurrent(Job job) {
        return isLatest(job)&&clock.getAsLong()-job.expiresAt()<0;
    }

    public synchronized boolean isLatest(Job job) {
        return job != null && latest.get(job.entityId()) == job;
    }

    public synchronized void clear() { latest.clear(); pending.clear(); }
}
