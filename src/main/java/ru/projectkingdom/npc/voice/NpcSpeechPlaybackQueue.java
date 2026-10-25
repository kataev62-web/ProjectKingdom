package ru.projectkingdom.npc.voice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded replacement queue which closes every displaced playback resource. */
public final class NpcSpeechPlaybackQueue<T extends AutoCloseable> {
    private final int maximum;
    private final LinkedHashMap<UUID,T> active=new LinkedHashMap<>();

    public NpcSpeechPlaybackQueue(int maximum){
        if(maximum<1)throw new IllegalArgumentException("maximum must be positive");
        this.maximum=maximum;
    }

    public synchronized void put(UUID entityId,T playback){
        if(entityId==null||playback==null)throw new IllegalArgumentException("playback is required");
        T replaced=active.remove(entityId);
        if(replaced!=null&&replaced!=playback)closeQuietly(replaced);
        if(active.size()>=maximum){
            Map.Entry<UUID,T> oldest=active.entrySet().iterator().next();
            active.remove(oldest.getKey());
            closeQuietly(oldest.getValue());
        }
        active.put(entityId,playback);
    }

    public synchronized boolean remove(UUID entityId,T expected){
        T current=active.get(entityId);
        if(current!=expected)return false;
        active.remove(entityId);
        return true;
    }

    public synchronized void stop(UUID entityId){
        T playback=active.remove(entityId);
        if(playback!=null)closeQuietly(playback);
    }

    public synchronized Map<UUID,T> entries(){return Map.copyOf(active);}

    public synchronized int size(){return active.size();}

    public synchronized void clear(){
        var values=active.values().toArray(AutoCloseable[]::new);
        active.clear();
        for(AutoCloseable value:values)closeQuietly(value);
    }

    private static void closeQuietly(AutoCloseable value){
        try{value.close();}catch(Exception ignored){}
    }
}
