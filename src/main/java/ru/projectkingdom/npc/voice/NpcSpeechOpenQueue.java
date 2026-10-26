package ru.projectkingdom.npc.voice;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded device-opening worker; every discarded task releases its temporary WAV. */
public final class NpcSpeechOpenQueue implements AutoCloseable {
    public interface Task extends Runnable,AutoCloseable {
        UUID entityId();
        @Override void close();
    }
    private final ThreadPoolExecutor executor;

    public NpcSpeechOpenQueue(int maximumPending){
        if(maximumPending<1)throw new IllegalArgumentException("maximum must be positive");
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maximumPending),r->{
                    Thread thread=new Thread(r,"kingdom-npc-speech-playback");
                    thread.setDaemon(true);return thread;
                });
    }

    public synchronized void submit(Task task){
        cancel(task.entityId());
        try{executor.execute(task);}
        catch(RejectedExecutionException full){task.close();}
    }

    public synchronized void cancel(UUID entityId){
        for(Runnable queued:executor.getQueue().toArray(Runnable[]::new)){
            Task task=(Task)queued;
            if(task.entityId().equals(entityId)&&executor.remove(task))task.close();
        }
    }

    public synchronized void clear(){
        for(Runnable queued:executor.getQueue().toArray(Runnable[]::new))
            if(executor.remove(queued))((Task)queued).close();
    }

    synchronized int pendingCount(){return executor.getQueue().size();}

    @Override public synchronized void close(){
        for(Runnable queued:executor.shutdownNow())((Task)queued).close();
    }
}
