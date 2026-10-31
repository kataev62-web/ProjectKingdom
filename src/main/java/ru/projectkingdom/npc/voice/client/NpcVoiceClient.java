package ru.projectkingdom.npc.voice.client;

import java.util.Optional;
import java.util.UUID;
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

/** Server-approved recordings: Minecraft audio only, no synthesis or external processes. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
        bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcVoiceClient {
    private static final NpcSpeechBubbleStore BUBBLES=new NpcSpeechBubbleStore(32);
    private static final NpcSpeechPlaybackQueue<NpcRecordedSound> PLAYBACK=new NpcSpeechPlaybackQueue<>(4);
    private static Object world;
    private static boolean reportedMissing;
    private NpcVoiceClient(){}

    public static void acceptSpeech(NpcSpeechPacket packet){
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)return;
        ensureWorld(minecraft);
        if(packet.automatic()&&PLAYBACK.entries().values().stream().anyMatch(sound->!sound.automatic()))return;
        if(!packet.automatic()){
            for(var entry:PLAYBACK.entries().entrySet())
                if(entry.getValue().automatic())BUBBLES.remove(entry.getKey());
            PLAYBACK.stopMatching(NpcRecordedSound::automatic);
        }
        PLAYBACK.stop(packet.entityId());
        BUBBLES.put(packet.entityId(),packet.name(),packet.text(),packet.gender(),
                minecraft.level.getGameTime(),packet.durationTicks(),packet.details());
        Entity npc=null;
        for(Entity entity:minecraft.level.entitiesForRendering())
            if(packet.entityId().equals(entity.getUUID())){npc=entity;break;}
        if(npc==null||minecraft.player==null||minecraft.player.distanceToSqr(npc)>256)return;
        var resource=new ResourceLocation(KingdomNpcMod.MOD_ID,
                "sounds/voice/"+packet.voice().id()+"/"+packet.line().id()+".ogg");
        if(minecraft.getResourceManager().getResource(resource).isEmpty()){
            if(!reportedMissing){
                reportedMissing=true;
                minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(
                        "Запись голоса отсутствует — читай облако над NPC"),false);
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
        BUBBLES.clear();PLAYBACK.clear();world=minecraft.level;reportedMissing=false;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft minecraft=Minecraft.getInstance();ensureWorld(minecraft);
        for(var entry:PLAYBACK.entries().entrySet()){
            var sound=entry.getValue();
            if(!sound.valid()||sound.isStopped()||sound.finished(minecraft.getSoundManager()))
                if(PLAYBACK.remove(entry.getKey(),sound))sound.close();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        BUBBLES.clear();PLAYBACK.clear();world=null;NpcVoiceKeyHandler.reset();
    }
}
