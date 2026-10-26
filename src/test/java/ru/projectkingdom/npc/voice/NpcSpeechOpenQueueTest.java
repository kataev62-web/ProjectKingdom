package ru.projectkingdom.npc.voice;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcSpeechOpenQueueTest {
    @Test void slowAudioDeviceCannotBuildAnUnboundedQueueOfWavFiles() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var queue=new NpcSpeechOpenQueue(2)){
            queue.submit(new Probe(new UUID(0,0),()->{
                started.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            }));
            assertTrue(started.await(2,TimeUnit.SECONDS));
            var tasks=new java.util.ArrayList<Probe>();
            for(int i=1;i<=100;i++){
                Probe task=new Probe(new UUID(0,i),()->{});tasks.add(task);queue.submit(task);
            }
            assertEquals(2,queue.pendingCount());
            assertEquals(98,tasks.stream().filter(p->p.closed).count());
            queue.clear();
            assertEquals(0,queue.pendingCount());
            assertTrue(tasks.stream().allMatch(p->p.closed));
        } finally{release.countDown();}
    }
    @Test void replacingPendingNpcSpeechDeletesItsOldWavBeforeOpening() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var queue=new NpcSpeechOpenQueue(2)){
            queue.submit(new Probe(new UUID(0,0),()->{
                started.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            }));
            assertTrue(started.await(2,TimeUnit.SECONDS));
            UUID npc=new UUID(0,1);
            Probe old=new Probe(npc,()->fail("Old reply must not open"));
            Probe next=new Probe(npc,()->{});
            queue.submit(old);queue.submit(next);
            assertTrue(old.closed);assertFalse(next.closed);
            assertEquals(1,queue.pendingCount());
            queue.cancel(npc);
            assertTrue(next.closed);assertEquals(0,queue.pendingCount());
        } finally{release.countDown();}
    }
    private static class Probe implements NpcSpeechOpenQueue.Task {
        private final UUID id;private final Runnable action;private volatile boolean closed;
        Probe(UUID id,Runnable action){this.id=id;this.action=action;}
        public UUID entityId(){return id;}
        public void run(){action.run();}
        public void close(){closed=true;}
    }
}
