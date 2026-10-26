package ru.projectkingdom.npc.voice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned bounded cooldown state. Client timestamps are never accepted. */
public final class NpcVoiceRateLimiter {
    private static final int DEFAULT_CAPACITY = 2048;
    private final int capacity;
    private final Map<UUID,Long> acceptedTicks = new LinkedHashMap<>();

    public NpcVoiceRateLimiter() { this(DEFAULT_CAPACITY); }

    NpcVoiceRateLimiter(int capacity) {
        if(capacity<1)throw new IllegalArgumentException("capacity must be positive");
        this.capacity=capacity;
    }

    public synchronized NpcVoiceRequestPolicy.Validation validateAndRecord(
            UUID playerId,String text,long serverTick) {
        if(playerId==null)return new NpcVoiceRequestPolicy.Validation(
                false,"",NpcVoiceRequestPolicy.Reason.BLANK);
        long last=acceptedTicks.getOrDefault(playerId,-1L);
        var result=NpcVoiceRequestPolicy.validate(text,serverTick,last);
        if(!result.accepted())return result;
        acceptedTicks.remove(playerId);
        acceptedTicks.put(playerId,serverTick);
        while(acceptedTicks.size()>capacity){
            var oldest=acceptedTicks.keySet().iterator();
            oldest.next();oldest.remove();
        }
        return result;
    }

    public synchronized void forget(UUID playerId) {
        if(playerId!=null)acceptedTicks.remove(playerId);
    }

    synchronized int size() { return acceptedTicks.size(); }
}
