package ru.projectkingdom.npc.voice.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.sounds.SoundSource;
import ru.projectkingdom.npc.voice.NpcSpeechOpenQueue;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.projectkingdom.npc.voice.NpcSpeechPlaybackQueue;
import ru.projectkingdom.npc.voice.NpcSpeechSpatialAudio;

/** Plays synthesized NPC WAV files off-thread with bounded spatial audio slots. */
public final class NpcSpeechPlayback {
    private static final int MAX_ACTIVE=8;
    private final NpcSpeechOpenQueue pending=new NpcSpeechOpenQueue(MAX_ACTIVE);
    private final NpcSpeechPlaybackQueue<ActivePlayback> active=
            new NpcSpeechPlaybackQueue<>(MAX_ACTIVE);

    public void play(UUID entityId,Path wav,BooleanSupplier canStart,BooleanSupplier valid){
        Minecraft minecraft=Minecraft.getInstance();
        minecraft.execute(()->{
            SpatialParameters spatial=locate(minecraft,entityId);
            if(!canStart.getAsBoolean()||spatial==null||spatial.volume()<=0.0D){delete(wav);return;}
            pending.submit(new NpcSpeechOpenQueue.Task(){
                public UUID entityId(){return entityId;}
                public void run(){open(entityId,wav,canStart,valid);}
                public void close(){delete(wav);}
            });
        });
    }

    public void clear(){pending.clear();active.clear();}
    public void stop(UUID entityId){pending.cancel(entityId);active.stop(entityId);}

    public void tick(){
        Minecraft minecraft=Minecraft.getInstance();
        for(var entry:active.entries().entrySet()){
            ActivePlayback playback=entry.getValue();
            SpatialParameters spatial=locate(minecraft,entry.getKey());
            if(!playback.valid.getAsBoolean()||spatial==null||spatial.volume()<=0.0D){
                if(active.remove(entry.getKey(),playback))playback.close();
            }else{
                applyVolume(playback.clip,spatial.volume());
                applyPan(playback.clip,spatial.pan());
            }
        }
    }

    private void open(UUID entityId,Path wav,BooleanSupplier canStart,BooleanSupplier valid){
        if(!canStart.getAsBoolean()){delete(wav);return;}
        Clip clip=null;
        ActivePlayback playback=null;
        try(var input=AudioSystem.getAudioInputStream(wav.toFile())){
            clip=AudioSystem.getClip();
            clip.open(input);
            playback=new ActivePlayback(clip,wav,valid);
            ActivePlayback accepted=playback;
            clip.addLineListener(event->{
                if(event.getType()==LineEvent.Type.STOP||event.getType()==LineEvent.Type.CLOSE)
                    finish(entityId,accepted);
            });
            Minecraft.getInstance().execute(()->{
                SpatialParameters spatial=locate(Minecraft.getInstance(),entityId);
                if(!canStart.getAsBoolean()||spatial==null||spatial.volume()<=0.0D){
                    accepted.close();return;
                }
                applyVolume(accepted.clip,spatial.volume());
                applyPan(accepted.clip,spatial.pan());
                active.put(entityId,accepted);
                accepted.clip.start();
            });
        } catch(Exception error) {
            if(playback!=null){
                active.remove(entityId,playback);
                playback.close();
            }else{
                if(clip!=null)clip.close();
                delete(wav);
            }
        }
    }

    private void finish(UUID entityId,ActivePlayback playback){
        if(active.remove(entityId,playback))playback.close();
    }

    private static SpatialParameters locate(Minecraft minecraft,UUID entityId){
        if(minecraft.level==null||minecraft.player==null)return null;
        Entity npc=null;
        for(Entity entity:minecraft.level.entitiesForRendering())
            if(entityId.equals(entity.getUUID())){npc=entity;break;}
        if(npc==null)return null;
        Vec3 delta=npc.position().subtract(minecraft.player.position());
        double distance=delta.length();
        Vec3 look=minecraft.player.getLookAngle();
        return new SpatialParameters(NpcSpeechSpatialAudio.volume(distance)
                *minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
                *minecraft.options.getSoundSourceVolume(SoundSource.NEUTRAL),
                NpcSpeechSpatialAudio.pan(delta.x,delta.z,look.x,look.z));
    }

    private static void applyVolume(Clip clip,double volume){
        if(!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))return;
        FloatControl control=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels=(float)(20.0D*Math.log10(Math.max(0.01D,volume)));
        control.setValue(Math.max(control.getMinimum(),Math.min(control.getMaximum(),decibels)));
    }

    private static void applyPan(Clip clip,double pan){
        if(!clip.isControlSupported(FloatControl.Type.PAN))return;
        FloatControl control=(FloatControl)clip.getControl(FloatControl.Type.PAN);
        control.setValue((float)Math.max(control.getMinimum(),Math.min(control.getMaximum(),pan)));
    }

    private static void delete(Path path){
        if(path==null)return;
        try{Files.deleteIfExists(path);}catch(Exception ignored){}
    }

    private record SpatialParameters(double volume,double pan) {}

    private static final class ActivePlayback implements AutoCloseable {
        private final Clip clip;
        private final Path wav;
        private final BooleanSupplier valid;
        private boolean closed;

        private ActivePlayback(Clip clip,Path wav,BooleanSupplier valid){
            this.clip=clip;this.wav=wav;this.valid=valid;
        }

        @Override public synchronized void close(){
            if(closed)return;
            closed=true;
            try{clip.stop();}finally{clip.close();delete(wav);}
        }
    }
}
