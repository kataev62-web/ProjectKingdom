package ru.projectkingdom.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.projectkingdom.network.KingdomCancelVolumeSelectionPacket;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.KingdomVolumeSelectionPacket;
import ru.projectkingdom.npc.KingdomNpcMod;

/** White world-space outline and size readout, matching the user's box selector.
 * Everything is visual until the server validates the second corner. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomVolumeSelectionOverlay {
    private KingdomVolumeSelectionOverlay(){}
    private static KingdomVolumeSelectionPacket current;
    private static String dimension;
    private static long expires;

    public static void accept(KingdomVolumeSelectionPacket packet){
        if(!packet.active()){current=null;dimension=null;return;}
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        current=packet;
        dimension=mc.level.dimension().location().toString();
        expires=mc.level.getGameTime()+20*60*10;
    }

    private static boolean active(){
        Minecraft mc=Minecraft.getInstance();
        if(current==null||mc.level==null||mc.player==null
                ||!mc.level.dimension().location().toString().equals(dimension)
                ||mc.level.getGameTime()>expires){
            current=null;return false;
        }
        return true;
    }
    private static BlockPos target(){
        Minecraft mc=Minecraft.getInstance();
        return mc.hitResult instanceof BlockHitResult b?b.getBlockPos():null;
    }
    private static AABB bounds(BlockPos first,BlockPos second){
        int x1=Math.min(first.getX(),second.getX()),x2=Math.max(first.getX(),second.getX());
        int y1=Math.min(first.getY(),second.getY()),y2=Math.max(first.getY(),second.getY());
        int z1=Math.min(first.getZ(),second.getZ()),z2=Math.max(first.getZ(),second.getZ());
        if(current.mode()==KingdomVolumeSelectionPacket.WAREHOUSE)y2=Math.max(y1+2,y2);
        return new AABB(x1,y1,z1,x2+1,y2+1,z2+1);
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!active())return;
        BlockPos aimed=target();if(aimed==null)return;
        BlockPos first=current.first()==null?aimed:current.first();
        if(first.distSqr(aimed)>100*100)return;
        var box=bounds(first,aimed);
        Minecraft mc=Minecraft.getInstance();
        var camera=e.getCamera().getPosition();
        PoseStack pose=e.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();
        VertexConsumer lines=buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose,lines,box,1F,1F,1F,0.96F);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
        if(!active())return;
        var aimed=target();
        var mc=Minecraft.getInstance();
        if(aimed==null)return;
        var first=current.first()==null?aimed:current.first();
        var box=bounds(first,aimed);
        long x=(long)box.getXsize(),y=(long)box.getYsize(),z=(long)box.getZsize();
        String label= switch(current.mode()){
            case KingdomVolumeSelectionPacket.WAREHOUSE->"Склад";
            case KingdomVolumeSelectionPacket.ENTERPRISE->"Здание";
            default->"Копирование";
        };
        String size=label+": "+(x*y*z)+" блоков ("+x+"x"+y+"x"+z+")";
        String help=current.first()==null?"ПКМ — первый угол | ЛКМ — отмена"
                :"ПКМ — подтвердить второй угол | ЛКМ — отмена";
        int center=e.getWindow().getGuiScaledWidth()/2;
        int y0=e.getWindow().getGuiScaledHeight()-94;
        e.getGuiGraphics().drawCenteredString(mc.font,size,center,y0,0xFFFFFF);
        e.getGuiGraphics().drawCenteredString(mc.font,help,center,y0+13,0x80FFFF);
    }
    @SubscribeEvent public static void cancel(InputEvent.MouseButton.Pre e){
        if(!active()||e.getButton()!=GLFW.GLFW_MOUSE_BUTTON_LEFT
                ||e.getAction()!=GLFW.GLFW_PRESS)return;
        current=null;dimension=null;
        KingdomNetwork.CHANNEL.sendToServer(new KingdomCancelVolumeSelectionPacket());
        e.setCanceled(true);
    }
}
