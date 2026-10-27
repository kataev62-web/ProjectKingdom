package ru.projectkingdom.npc.voice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.npc.voice.NpcVoiceBank;
import ru.projectkingdom.npc.voice.NpcVoiceLine;
import ru.projectkingdom.npc.voice.NpcVoiceRecordings;

/** Short cached Vorbis sound attached to an NPC and the exact client-world instance. */
final class NpcRecordedSound extends AbstractTickableSoundInstance implements AutoCloseable {
    private final Entity npc;
    private final Object world;
    private final long createdAt=System.nanoTime();
    private final long deadline;
    private boolean closed;
    private final boolean automatic;

    NpcRecordedSound(Entity npc,NpcVoiceBank bank,NpcVoiceLine line,int durationTicks,boolean automatic){
        super(SoundEvent.createVariableRangeEvent(new ResourceLocation(KingdomNpcMod.MOD_ID,
                NpcVoiceRecordings.soundId(bank,line))),SoundSource.NEUTRAL,RandomSource.create());
        this.npc=npc;this.world=npc.level();
        this.automatic=automatic;
        this.deadline=createdAt+durationTicks*50_000_000L;
        volume=1F;pitch=1F;looping=false;relative=false;
        updatePosition();
    }
    boolean valid(){
        Minecraft minecraft=Minecraft.getInstance();
        return !closed&&minecraft.level==world&&minecraft.player!=null&&npc.isAlive()
                &&!npc.isRemoved()&&minecraft.player.distanceToSqr(npc)<256
                &&minecraft.options.getSoundSourceVolume(SoundSource.MASTER)>0F
                &&minecraft.options.getSoundSourceVolume(SoundSource.NEUTRAL)>0F
                &&System.nanoTime()-deadline<0;
    }
    boolean automatic(){return automatic;}
    boolean finished(SoundManager manager){
        return System.nanoTime()-createdAt>1_000_000_000L&&!manager.isActive(this);
    }
    @Override public void tick(){if(!valid())stop();else updatePosition();}
    private void updatePosition(){x=npc.getX();y=npc.getEyeY();z=npc.getZ();}
    @Override public void close(){
        if(closed)return;
        closed=true;stop();Minecraft.getInstance().getSoundManager().stop(this);
    }
}
