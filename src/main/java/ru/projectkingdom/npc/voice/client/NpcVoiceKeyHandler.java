package ru.projectkingdom.npc.voice.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.NpcVoiceRequestPacket;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.npc.voice.NpcVoiceSession;

/** Remappable hold-V push-to-talk controller. Raw PCM remains on the client. */
public final class NpcVoiceKeyHandler {
    private static final KeyMapping KEY=new KeyMapping("key.kingdomnpc.talk",
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.kingdomnpc");
    private static final WindowsMicrophoneCapture capture=new WindowsMicrophoneCapture();
    private static final VoskRussianRecognizer recognizer=new VoskRussianRecognizer();
    private static boolean wasDown;
    private static final NpcVoiceSession SESSION=new NpcVoiceSession();
    private static long token;
    private static long startedNanos;
    private static Object world;
    private static Optional<UUID> target=Optional.empty();

    private NpcVoiceKeyHandler() {}

    public static void warmUp(){
        if(System.getProperty("os.name","").startsWith("Windows"))
            recognizer.warmUp().exceptionally(error->{return null;});
    }

    private static void tick(){
        Minecraft minecraft=Minecraft.getInstance();
        if(world!=minecraft.level){reset();world=minecraft.level;}
        boolean down=KEY.isDown();
        if(minecraft.player==null||minecraft.level==null||minecraft.screen!=null
                ||!minecraft.isWindowActive()){
            reset();wasDown=down;return;
        }
        if(down&&!wasDown)begin(minecraft);
        if(SESSION.isRecording()&&(!down||!capture.isRecording()
                ||System.nanoTime()-startedNanos>=8_000_000_000L))finish(minecraft);
        wasDown=down;
    }

    private static void begin(Minecraft minecraft){
        long next=SESSION.begin();
        if(next<0)return;
        token=next;startedNanos=System.nanoTime();
        target=NpcVoiceTargeting.target(minecraft);
        status(minecraft,"Слушаю…");
        capture.start().whenComplete((ignored,error)->{
            if(error!=null)minecraft.execute(()->{
                if(token==next&&SESSION.isRecording()){
                    SESSION.cancel();target=Optional.empty();
                    status(minecraft,"Микрофон недоступен");
                }
            });
        });
    }

    private static void finish(Minecraft minecraft){
        long finished=token;
        if(!SESSION.finish(finished))return;
        Object connectionWorld=minecraft.level;
        status(minecraft,"Распознаю…");
        Optional<UUID> selected=target;
        capture.stop().thenCompose(recognizer::recognize).whenComplete((text,error)->
            minecraft.execute(()->{
                if(!SESSION.complete(finished))return;
                target=Optional.empty();
                if(minecraft.level!=connectionWorld||minecraft.player==null)return;
                if(error!=null){status(minecraft,"Не удалось распознать речь");return;}
                if(text==null||text.isBlank()){status(minecraft,"Речь не распознана");return;}
                if(text.length()>256){status(minecraft,"Скажи короче — до 256 символов");return;}
                KingdomNetwork.CHANNEL.sendToServer(
                        new NpcVoiceRequestPacket(selected.orElse(null),text));
                status(minecraft,"Сказано: "+text);
            }));
    }

    public static void reset(){
        SESSION.cancel();target=Optional.empty();wasDown=false;
        capture.stop();
    }

    private static void status(Minecraft minecraft,String message){
        minecraft.gui.setOverlayMessage(Component.literal(message),false);
    }

    @Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
            bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(KEY);}
    }

    @Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
            bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class RuntimeEvents {
        private RuntimeEvents() {}
        @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event){
            if(event.phase==TickEvent.Phase.END)tick();
        }
    }
}
