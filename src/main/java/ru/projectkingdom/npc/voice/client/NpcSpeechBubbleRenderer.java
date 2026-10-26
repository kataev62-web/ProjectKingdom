package ru.projectkingdom.npc.voice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import ru.projectkingdom.npc.KingdomNpcMod;

/** Camera-facing high-contrast Russian subtitle bubble above a speaking NPC. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
        bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcSpeechBubbleRenderer {
    private static final double MAX_DISTANCE_SQUARED=256.0;
    private static final int NAME_COLOR=0xFFD7A33D;
    private static final int TEXT_COLOR=0xFFFFFFFF;

    private NpcSpeechBubbleRenderer() {}

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?,?> event){
        if(!(event.getEntity() instanceof Villager villager))return;
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.player==null||minecraft.level==null
                ||minecraft.player.distanceToSqr(villager)>MAX_DISTANCE_SQUARED)return;
        long now=minecraft.level.getGameTime();
        var optional=NpcVoiceClient.speech(villager.getUUID(),now);
        if(optional.isEmpty())return;
        var speech=optional.get();
        float fade=NpcVoiceClient.alpha(speech,now);
        if(fade<=0F)return;

        Font font=minecraft.font;
        var lines=new ArrayList<FormattedCharSequence>();
        lines.add(Component.literal(speech.name()).getVisualOrderText());
        lines.addAll(font.split(Component.literal(speech.text()),220));
        int totalHeight=lines.size()*10;

        PoseStack pose=event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D,villager.getBbHeight()+0.82D,0.0D);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F,-0.025F,0.025F);
        Matrix4f matrix=pose.last().pose();
        int alpha=Math.max(0,Math.min(255,(int)(fade*255F)));
        int background=(Math.max(0,Math.min(255,(int)(fade*168F)))<<24);
        for(int i=0;i<lines.size();i++){
            FormattedCharSequence line=lines.get(i);
            float x=-font.width(line)/2.0F;
            float y=i*10-totalHeight;
            int rgb=i==0?NAME_COLOR:TEXT_COLOR;
            int color=(alpha<<24)|(rgb&0x00FFFFFF);
            font.drawInBatch(line,x,y,color,false,matrix,event.getMultiBufferSource(),
                    Font.DisplayMode.NORMAL,background,event.getPackedLight());
        }
        pose.popPose();
    }
}
