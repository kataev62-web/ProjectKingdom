package ru.projectkingdom.npc.voice.client;

import java.util.Optional;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.network.NpcSpeechPacket;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.npc.voice.NpcSpeechBubbleStore;
import ru.projectkingdom.npc.voice.NpcSpeechPlaybackQueue;
import ru.projectkingdom.npc.voice.NpcSpeechTrackingQueue;
import ru.projectkingdom.npc.voice.NpcVoiceRecordings;

/** Server-approved recordings: Minecraft audio only, no synthesis or external processes. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
        bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcVoiceClient {
    private static final NpcSpeechBubbleStore BUBBLES=new NpcSpeechBubbleStore(32);
    private static final NpcSpeechPlaybackQueue<NpcRecordedSound> PLAYBACK=new NpcSpeechPlaybackQueue<>(4);
    private static final NpcSpeechTrackingQueue<NpcSpeechPacket> WAITING=new NpcSpeechTrackingQueue<>(32,40);
    private static final org.slf4j.Logger LOGGER=LogUtils.getLogger();
    private static Object world;
    private static boolean reportedMissingClip,reportedMissingEvent,reportedUntracked;
    private NpcVoiceClient(){}

    public static void acceptSpeech(NpcSpeechPacket packet){
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)return;
        ensureWorld(minecraft);
        if(packet.automatic()&&(PLAYBACK.entries().values().stream().anyMatch(sound->!sound.automatic())
                ||WAITING.anyMatch(waiting->!waiting.automatic())))return;
        if(!packet.automatic()){
            for(var entry:PLAYBACK.entries().entrySet())
                if(entry.getValue().automatic())BUBBLES.remove(entry.getKey());
            PLAYBACK.stopMatching(NpcRecordedSound::automatic);
            WAITING.removeIf(waiting->{
                if(!waiting.automatic())return false;
                BUBBLES.remove(waiting.entityId());
                return true;
            });
        }
        PLAYBACK.stop(packet.entityId());
        WAITING.remove(packet.entityId());
        putBubble(minecraft,packet);
        Entity npc=null;
        for(Entity entity:minecraft.level.entitiesForRendering())
            if(packet.entityId().equals(entity.getUUID())){npc=entity;break;}
        if(npc==null){
            WAITING.offer(packet.entityId(),packet,minecraft.level.getGameTime());
            return;
        }
        playSpeech(minecraft,packet,npc);
    }

    private static void putBubble(Minecraft minecraft,NpcSpeechPacket packet){
        BUBBLES.put(packet.entityId(),packet.name(),packet.text(),packet.gender(),
                minecraft.level.getGameTime(),packet.durationTicks(),packet.details());
    }

    private static void playSpeech(Minecraft minecraft,NpcSpeechPacket packet,Entity npc){
        if(minecraft.player==null||minecraft.player.distanceToSqr(npc)>256)return;
        var resource=new ResourceLocation(KingdomNpcMod.MOD_ID,
                "sounds/voice/"+packet.voice().id()+"/"+packet.line().id()+".ogg");
        if(minecraft.getResourceManager().getResource(resource).isEmpty()){
            if(!reportedMissingClip){
                reportedMissingClip=true;
                LOGGER.warn("NPC voice clip is missing: {} (NPC {}). Speech bubble remains available.",resource,packet.entityId());
                minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Запись голоса отсутствует — читай облако над NPC"),false);
            }
            return;
        }
        var event=new ResourceLocation(KingdomNpcMod.MOD_ID,NpcVoiceRecordings.soundId(packet.voice(),packet.line()));
        if(minecraft.getSoundManager().getSoundEvent(event)==null){
            if(!reportedMissingEvent){
                reportedMissingEvent=true;
                LOGGER.warn("NPC voice sound event is missing from loaded sounds.json: {} (NPC {}). Clip {} exists.",
                        event,packet.entityId(),resource);
                minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Событие голоса не загружено — читай облако над NPC"),false);
            }
            return;
        }
        var sound=new NpcRecordedSound(npc,packet.voice(),packet.line(),packet.durationTicks(),packet.automatic());
        PLAYBACK.put(packet.entityId(),sound);
        minecraft.getSoundManager().play(sound);
    }

    static Optional<NpcSpeechBubbleStore.Speech> speech(UUID entityId,long nowTick){
        return BUBBLES.find(entityId,nowTick);
    }
    static float alpha(NpcSpeechBubbleStore.Speech speech,long nowTick){return BUBBLES.alpha(speech,nowTick);}

    private static void ensureWorld(Minecraft minecraft){
        if(world==minecraft.level)return;
        BUBBLES.clear();PLAYBACK.clear();WAITING.clear();world=minecraft.level;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft minecraft=Minecraft.getInstance();ensureWorld(minecraft);
        if(minecraft.level!=null&&!WAITING.isEmpty()){
            var tracked=new java.util.HashMap<UUID,Entity>();
            for(Entity entity:minecraft.level.entitiesForRendering())tracked.put(entity.getUUID(),entity);
            for(var packet:WAITING.takeReady(minecraft.level.getGameTime(),tracked::containsKey,NpcVoiceClient::expired)){
                putBubble(minecraft,packet);
                playSpeech(minecraft,packet,tracked.get(packet.entityId()));
            }
        }
        for(var entry:PLAYBACK.entries().entrySet()){
            var sound=entry.getValue();
            if(!sound.valid()||sound.isStopped()||sound.finished(minecraft.getSoundManager()))
                if(PLAYBACK.remove(entry.getKey(),sound))sound.close();
        }
    }
    private static void expired(NpcSpeechPacket packet){
        if(reportedUntracked)return;
        reportedUntracked=true;
        LOGGER.warn("NPC voice expired while waiting 40 ticks for entity tracking: NPC {}, voice {}, line {}.",
                packet.entityId(),packet.voice().id(),packet.line().id());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        BUBBLES.clear();PLAYBACK.clear();WAITING.clear();world=null;
        reportedMissingClip=false;reportedMissingEvent=false;reportedUntracked=false;NpcVoiceKeyHandler.reset();
    }
}
