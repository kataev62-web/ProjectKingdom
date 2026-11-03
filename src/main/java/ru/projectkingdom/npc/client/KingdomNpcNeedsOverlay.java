package ru.projectkingdom.npc.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.network.KingdomNpcNeedsPacket;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.npc.voice.client.NpcVoiceClient;

/** Compact persistent notification above the registered NPC, not the whole village. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
        bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomNpcNeedsOverlay {
    private KingdomNpcNeedsOverlay(){}
    private record Entry(String text,boolean warning,long until,String dimension){}
    private static final Map<UUID,Entry> LAST=new ConcurrentHashMap<>();

    public static void accept(KingdomNpcNeedsPacket p){
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        LAST.put(p.npc(),new Entry(p.text(),p.warning(),mc.level.getGameTime()+130,
                mc.level.dimension().location().toString()));
    }

    @SubscribeEvent public static void render(RenderLivingEvent.Post<?,?> e){
        if(!(e.getEntity() instanceof Villager npc))return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.player.distanceToSqr(npc)>20*20)return;
        Entry text=LAST.get(npc.getUUID());
        if(text==null)return;
        if(mc.level.getGameTime()>text.until()
                ||!text.dimension().equals(mc.level.dimension().location().toString())){
            LAST.remove(npc.getUUID());return;
        }
        // Spoken replies have priority over the ordinary status bubble.
        if(NpcVoiceClient.speech(npc.getUUID(),mc.level.getGameTime()).isPresent())return;
        var pose=e.getPoseStack();
        pose.pushPose();
        pose.translate(0,npc.getBbHeight()+0.65D,0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.026f,-0.026f,0.026f);
        var sequence=Component.literal(text.text()).getVisualOrderText();
        float x=-mc.font.width(sequence)*0.5f;
        mc.font.drawInBatch(sequence,x,-12,
                text.warning()?0xFFFFD49C:0xFFB9F2C3,false,
                pose.last().pose(),e.getMultiBufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0x80000000,e.getPackedLight());
        pose.popPose();
    }
}
