package ru.projectkingdom.npc.voice.client;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;

/** One bounded push-to-talk capture using the Windows default microphone. */
public final class WindowsMicrophoneCapture {
    static final int MAX_PCM_BYTES=16000*2*8;
    private static final AudioFormat FORMAT=new AudioFormat(16000F,16,1,true,false);
    @FunctionalInterface interface Device { TargetDataLine open() throws Exception; }
    private final Device device;
    private final BooleanSupplier supported;
    private final Executor executor;
    private volatile Capture current;

    public WindowsMicrophoneCapture(){
        this(()->AudioSystem.getTargetDataLine(FORMAT),
                ()->System.getProperty("os.name","").startsWith("Windows"),
                Executors.newSingleThreadExecutor(r->{
                    Thread thread=new Thread(r,"kingdom-npc-microphone");
                    thread.setDaemon(true);return thread;
                }));
    }

    WindowsMicrophoneCapture(Device device,BooleanSupplier supported,Executor executor){
        this.device=device;this.supported=supported;this.executor=executor;
    }

    public synchronized CompletableFuture<Void> start(){
        if(!supported.getAsBoolean())return CompletableFuture.failedFuture(
                new IllegalStateException("Voice input requires Windows"));
        if(current!=null&&!current.result.isDone())return CompletableFuture.failedFuture(
                new IllegalStateException("Microphone is already recording"));
        Capture capture=new Capture();current=capture;
        executor.execute(()->capture(capture));
        return capture.started;
    }

    public CompletableFuture<byte[]> stop(){
        Capture capture=current;
        if(capture==null)return CompletableFuture.completedFuture(new byte[0]);
        capture.recording=false;
        close(capture.line);
        return capture.result;
    }

    public boolean isRecording(){
        Capture capture=current;
        return capture!=null&&capture.recording;
    }

    private void capture(Capture capture){
        var output=new ByteArrayOutputStream(MAX_PCM_BYTES);
        TargetDataLine opened=null;
        Throwable failure=null;
        try {
            if(capture.recording){
                opened=device.open();opened.open(FORMAT,4096);opened.start();
                capture.line=opened;
            }
            capture.started.complete(null);
            byte[] buffer=new byte[2048];
            while(capture.recording&&output.size()<MAX_PCM_BYTES){
                int read=opened.read(buffer,0,Math.min(buffer.length,MAX_PCM_BYTES-output.size()));
                if(read<0)break;
                if(read>0)output.write(buffer,0,read);
            }
        } catch(Exception error) {
            if(capture.recording)failure=error;
        } finally {
            capture.recording=false;
            close(opened);capture.line=null;
            if(failure==null){
                capture.started.complete(null);capture.result.complete(output.toByteArray());
            } else {
                capture.started.completeExceptionally(failure);
                capture.result.completeExceptionally(failure);
            }
        }
    }

    private static void close(TargetDataLine line){
        if(line==null)return;
        try{line.stop();}catch(Exception ignored){}
        try{line.close();}catch(Exception ignored){}
    }

    private static final class Capture {
        private volatile boolean recording=true;
        private volatile TargetDataLine line;
        private final CompletableFuture<Void> started=new CompletableFuture<>();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
    }
}
