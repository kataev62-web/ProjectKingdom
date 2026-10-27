package ru.projectkingdom.npc.voice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure bounded lifetime state used by the client world renderer. */
public final class NpcSpeechBubbleStore {
    public static final int FADE_TICKS=30;
    private final int capacity;
    private final Map<UUID,Speech> active=new LinkedHashMap<>();

    public record Speech(UUID entityId,String name,String text,String gender,
                         long startTick,long endTick,String details) {}

    public NpcSpeechBubbleStore(int capacity){
        if(capacity<1)throw new IllegalArgumentException("capacity must be positive");
        this.capacity=capacity;
    }

    public synchronized void put(UUID entityId,String name,String text,String gender,
                                 long nowTick,int durationTicks){
        put(entityId,name,text,gender,nowTick,durationTicks,"");
    }

    public synchronized void put(UUID entityId,String name,String text,String gender,
                                 long nowTick,int durationTicks,String details){
        if(entityId==null||text==null||text.isBlank())return;
        prune(nowTick);
        active.remove(entityId);
        active.put(entityId,new Speech(entityId,name==null?"":name,text,
                gender==null?"unspecified":gender,nowTick,nowTick+durationTicks,details==null?"":details));
        while(active.size()>capacity){
            var oldest=active.keySet().iterator();oldest.next();oldest.remove();
        }
    }

    public synchronized Optional<Speech> find(UUID entityId,long nowTick){
        prune(nowTick);return Optional.ofNullable(active.get(entityId));
    }

    public synchronized List<Speech> all(long nowTick){
        prune(nowTick);return List.copyOf(new ArrayList<>(active.values()));
    }

    public synchronized int size(long nowTick){prune(nowTick);return active.size();}
    public synchronized void clear(){active.clear();}

    public float alpha(Speech speech,long nowTick){
        long remaining=speech.endTick-nowTick;
        if(remaining<=0)return 0F;
        if(remaining>=FADE_TICKS)return 1F;
        return remaining/(float)FADE_TICKS;
    }

    private void prune(long nowTick){active.values().removeIf(speech->nowTick>=speech.endTick);}
}
