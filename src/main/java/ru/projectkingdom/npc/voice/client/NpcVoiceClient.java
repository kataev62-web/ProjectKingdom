package ru.projectkingdom.npc.voice.client;

import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.event.TickEvent;
import ru.projectkingdom.npc.voice.NpcSpeechJobs;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.network.NpcSpeechPacket;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.npc.voice.NpcSpeechBubbleStore;

/** Client lifecycle boundary for server-approved NPC speech. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
        bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcVoiceClient {
    private static final NpcSpeechBubbleStore BUBBLES=new NpcSpeechBubbleStore(32);
    private static final WindowsSpeechSynthesizer SPEECH=new WindowsSpeechSynthesizer();
    private static final NpcSpeechPlayback PLAYBACK=new NpcSpeechPlayback();

    private static final NpcSpeechJobs JOBS=new NpcSpeechJobs(8);
    private static boolean synthesizing;
    private static Object world;
    private static boolean reportedSpeechFailure;

    private NpcVoiceClient() {}

    public static void acceptSpeech(NpcSpeechPacket packet){
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)return;
        ensureWorld(minecraft);
        BUBBLES.put(packet.entityId(),packet.name(),packet.text(),packet.gender(),
                minecraft.level.getGameTime(),packet.durationTicks());
        JOBS.offer(packet.entityId(),packet.text(),packet.gender(),packet.durationTicks());
        PLAYBACK.stop(packet.entityId());
        pump(minecraft);
    }

    static Optional<NpcSpeechBubbleStore.Speech> speech(UUID entityId,long nowTick){
        return BUBBLES.find(entityId,nowTick);
    }

    static float alpha(NpcSpeechBubbleStore.Speech speech,long nowTick){
        return BUBBLES.alpha(speech,nowTick);
    }

    private static void pump(Minecraft minecraft){
        if(synthesizing||minecraft.level==null)return;
        var job=JOBS.poll();
        if(job==null)return;
        synthesizing=true;
        Object connectionWorld=minecraft.level;
        SPEECH.synthesize(job.text(),job.gender()).whenComplete((wav,error)->
            minecraft.execute(()->{
                synthesizing=false;
                if(error==null&&JOBS.isCurrent(job)&&minecraft.level==connectionWorld){
                    PLAYBACK.play(job.entityId(),wav,
                            ()->JOBS.isCurrent(job)&&minecraft.level==connectionWorld,
                            ()->JOBS.isLatest(job)&&minecraft.level==connectionWorld);
                }else{
                    delete(wav);
                    if(error!=null&&JOBS.isCurrent(job)&&minecraft.level==connectionWorld
                            &&!reportedSpeechFailure){
                        reportedSpeechFailure=true;
                        minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(
                                "Озвучка недоступна — читай облако над NPC"),false);
                    }
                }
                pump(minecraft);
            }));
    }

    private static void delete(Path path){
        if(path!=null)try{Files.deleteIfExists(path);}catch(Exception ignored){}
    }

    private static void ensureWorld(Minecraft minecraft){
        if(world==minecraft.level)return;
        BUBBLES.clear();JOBS.clear();PLAYBACK.clear();
        world=minecraft.level;reportedSpeechFailure=false;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft minecraft=Minecraft.getInstance();
        ensureWorld(minecraft);
        PLAYBACK.tick();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        BUBBLES.clear();JOBS.clear();
        PLAYBACK.clear();world=null;
        NpcVoiceKeyHandler.reset();
    }
}
