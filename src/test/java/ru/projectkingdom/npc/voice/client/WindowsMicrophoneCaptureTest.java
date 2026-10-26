package ru.projectkingdom.npc.voice.client;

import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import javax.sound.sampled.TargetDataLine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsMicrophoneCaptureTest {
    @Test void releaseDuringDeviceOpenClosesTheDeviceAndCompletesEmptyCapture() throws Exception {
        CountDownLatch opening=new CountDownLatch(1), release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        TargetDataLine line=(TargetDataLine)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{TargetDataLine.class},(proxy,method,args)->{
                    if(method.getName().equals("open")){opening.countDown();release.await(2,TimeUnit.SECONDS);}
                    if(method.getName().equals("close"))closed.set(true);
                    if(method.getName().equals("read"))fail("Cancelled capture must not read");
                    return null;
                });
        try {
            var capture=new WindowsMicrophoneCapture(()->line,()->true,executor);
            var started=capture.start();
            assertTrue(opening.await(2,TimeUnit.SECONDS));
            var result=capture.stop();
            release.countDown();
            assertEquals(0,result.get(2,TimeUnit.SECONDS).length);
            started.get(2,TimeUnit.SECONDS);
            assertTrue(closed.get());
            assertFalse(capture.isRecording());
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void reachingByteLimitCompletesWithoutWaitingForKeyRelease() throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        TargetDataLine line=(TargetDataLine)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{TargetDataLine.class},(proxy,method,args)->
                        method.getName().equals("read")?(Integer)args[2]:null);
        try {
            var capture=new WindowsMicrophoneCapture(()->line,()->true,executor);
            capture.start().get(2,TimeUnit.SECONDS);
            executor.submit(()->{}).get(2,TimeUnit.SECONDS);
            assertFalse(capture.isRecording());
            assertEquals(256000,capture.stop().get(2,TimeUnit.SECONDS).length);
        } finally {executor.shutdownNow();}
    }
}
