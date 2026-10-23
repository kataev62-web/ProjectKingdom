package ru.projectkingdom.npc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.projectkingdom.network.ArchitectDecisionPacket;
import ru.projectkingdom.network.ArchitectPreviewPacket;
import ru.projectkingdom.network.ArchitectResultPacket;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.npc.KingdomArchitectGeometry;
import ru.projectkingdom.npc.KingdomBlockStateCodec;
import ru.projectkingdom.npc.KingdomNpcMod;

/**
 * Client-only real block-model preview. Does not set any world block.
 * F8 reopens architect controls while walking around the 3D ghost.
 */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,
        bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomArchitectClient {
    private KingdomArchitectClient() {}

    public static final class View {
        public record ModelCell(int x,int y,int z,BlockState state) {}
        public final ArchitectPreviewPacket packet;
        public final List<ModelCell> blocks;
        public BlockPos origin;
        public int rotation;
        public boolean pending;

        private View(ArchitectPreviewPacket packet,List<ModelCell> blocks){
            this.packet=packet;this.blocks=List.copyOf(blocks);this.origin=packet.origin();
        }
        public void move(int dx,int dy,int dz) {
            if(pending)return;
            var next=origin.offset(dx,dy,dz);
            if(Math.abs((long)next.getX()-packet.marker().getX())>80
                    || Math.abs((long)next.getZ()-packet.marker().getZ())>80
                    || Math.abs((long)next.getY()-packet.marker().getY())>32)return;
            origin=next;
        }
        public void rotate() {if(!pending)rotation=(rotation+1)%4;}
        public KingdomArchitectGeometry.Footprint footprint(){
            return KingdomArchitectGeometry.footprint(packet.width(),packet.length(),rotation);
        }
    }
    private static View active;

    public static View current(){return active;}
    public static void accept(ArchitectPreviewPacket packet){
        Minecraft client=Minecraft.getInstance();
        if(client.player==null)return;
        var palette=new ArrayList<BlockState>();
        try {
            for(var spec:packet.palette())palette.add(KingdomBlockStateCodec.parse(spec));
        } catch(IllegalArgumentException error) {
            client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Неизвестные блоки для 3D-предпросмотра"),false);
            return;
        }
        var blocks=new ArrayList<View.ModelCell>(packet.cells().size());
        for(var cell:packet.cells())blocks.add(new View.ModelCell(
            cell.x(),cell.y(),cell.z(),palette.get(cell.state())));
        active=new View(packet,blocks);
        client.player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "[Архитектор] 3D-модель «"+packet.title()+"» получена: "
                    +blocks.size()+" блоков визуализации. Осмотр — F8."),false);
        // Closing the server menu here is also done by the atlas client.
        if(client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
            client.player.closeContainer();
        client.setScreen(new KingdomArchitectScreen());
    }

    public static void decide(boolean cancel){
        if(active==null || active.pending)return;
        active.pending=true;
        KingdomNetwork.CHANNEL.sendToServer(new ArchitectDecisionPacket(
            active.packet.token(),active.origin,active.rotation,cancel));
    }

    public static void result(ArchitectResultPacket answer){
        var client=Minecraft.getInstance();
        if(active==null)return;
        active.pending=false;
        if(answer.accepted()){
            active=null;
            if(client.screen instanceof KingdomArchitectScreen)
                client.setScreen(null);
        } else if(client.screen instanceof KingdomArchitectScreen screen) {
            screen.error(answer.message());
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        active=null;
    }

    @SubscribeEvent public static void reopen(InputEvent.Key event){
        if(active==null || event.getAction()!=GLFW.GLFW_PRESS
                || event.getKey()!=GLFW.GLFW_KEY_F8)return;
        var minecraft=Minecraft.getInstance();
        if(minecraft.player!=null && minecraft.screen==null)
            minecraft.setScreen(new KingdomArchitectScreen());
    }

    private static Rotation rotation(int turn){
        return switch(turn){
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    @SubscribeEvent public static void renderWorld(RenderLevelStageEvent event){
        var view=active;
        var client=Minecraft.getInstance();
        if(view==null || client.level==null || client.player==null
                || event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var eye=event.getCamera().getPosition();
        var rotation=rotation(view.rotation);
        var pose=event.getPoseStack();
        var buffer=client.renderBuffers().bufferSource();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(.48F,.90F,1.0F,.58F);
        int rendered=0;
        for(var cell:view.blocks){
            var translated=KingdomArchitectGeometry.rotate(cell.x(),cell.z(),
                view.packet.width(),view.packet.length(),view.rotation);
            int x=view.origin.getX()+translated.x(),y=view.origin.getY()+cell.y();
            int z=view.origin.getZ()+translated.z();
            if(eye.distanceToSqr(x+.5,y+.5,z+.5)>95*95)continue;
            pose.pushPose();
            pose.translate(x-eye.x,y-eye.y,z-eye.z);
            client.getBlockRenderer().renderSingleBlock(cell.state().rotate(rotation),
                pose,buffer,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if(++rendered>=2400)break;
        }
        buffer.endBatch();
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.disableBlend();
        // A clearly recognizable outline anchors the ghost to the real terrain.
        var size=view.footprint();
        pose.pushPose();
        pose.translate(-eye.x,-eye.y,-eye.z);
        AABB bounds=new AABB(view.origin.getX(),view.origin.getY(),view.origin.getZ(),
            view.origin.getX()+size.width(),view.origin.getY()+view.packet.height(),
            view.origin.getZ()+size.length());
        VertexConsumer lines=buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose,lines,bounds,.2F,.92F,1F,.75F);
        buffer.endBatch(RenderType.lines());
        pose.popPose();
    }
}
