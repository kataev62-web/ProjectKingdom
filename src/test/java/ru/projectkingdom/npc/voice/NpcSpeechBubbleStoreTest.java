package ru.projectkingdom.npc.voice;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcSpeechBubbleStoreTest {
    @Test void newerSpeechReplacesOlderSpeechFromSameNpc() {
        var store=new NpcSpeechBubbleStore(8);
        UUID npc=UUID.randomUUID();
        store.put(npc,"Анна","Старая реплика","female",100,100);
        store.put(npc,"Анна","Новая реплика","female",110,120);
        assertEquals(1,store.size(110));
        assertEquals("Новая реплика",store.find(npc,110).orElseThrow().text());
    }

    @Test void differentNpcsCanSpeakAtTheSameTime() {
        var store=new NpcSpeechBubbleStore(8);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        store.put(first,"Анна","Привет","female",100,100);
        store.put(second,"Борис","Здравствуй","male",100,100);
        assertEquals(2,store.size(100));
        assertTrue(store.find(first,100).isPresent());
        assertTrue(store.find(second,100).isPresent());
    }

    @Test void speechExpiresAtItsEndTickAndFadesForLastThirtyTicks() {
        var store=new NpcSpeechBubbleStore(8);
        UUID npc=UUID.randomUUID();
        store.put(npc,"Анна","Привет","female",100,100);
        var speech=store.find(npc,170).orElseThrow();
        assertEquals(Float.floatToIntBits(1.0F),Float.floatToIntBits(store.alpha(speech,170)));
        assertEquals(Float.floatToIntBits(0.5F),Float.floatToIntBits(store.alpha(speech,185)));
        assertFalse(store.find(npc,200).isPresent());
    }

    @Test void evictsOldestSpeechAtHardCapacity() {
        var store=new NpcSpeechBubbleStore(2);
        UUID first=new UUID(0,1),second=new UUID(0,2),third=new UUID(0,3);
        store.put(first,"А","1","female",1,100);
        store.put(second,"Б","2","male",2,100);
        store.put(third,"В","3","male",3,100);
        assertFalse(store.find(first,3).isPresent());
        assertTrue(store.find(second,3).isPresent());
        assertTrue(store.find(third,3).isPresent());
    }
}
