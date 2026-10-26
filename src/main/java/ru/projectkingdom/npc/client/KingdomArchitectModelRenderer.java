package ru.projectkingdom.npc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.network.ArchitectPreviewPacket;
import ru.projectkingdom.npc.KingdomBlockStateCodec;

/**
 * GUI-sized scene drawn from actual Minecraft block models and atlas textures.
 *
 * Crucially, this does NOT project one colored dot per block: stairs, roofs,
 * wood planks and windows are rendered using the client block model renderer.
 * It changes no world block and performs no world chunk lookup.
 */
public final class KingdomArchitectModelRenderer {
    private KingdomArchitectModelRenderer() {}

    public record ModelCell(int x,int y,int z,BlockState state) {}
    public record Model(List<ModelCell> blocks,int width,int height,int length) {}

    public static Model decode(ArchitectPreviewPacket packet) {
        var states=new ArrayList<BlockState>();
        for(String encoded:packet.palette())
            states.add(KingdomBlockStateCodec.parse(encoded));
        var blocks=new ArrayList<ModelCell>(packet.cells().size());
        for(var cell:packet.cells())
            blocks.add(new ModelCell(cell.x(),cell.y(),cell.z(),states.get(cell.state())));
        return new Model(List.copyOf(blocks),packet.width(),packet.height(),packet.length());
    }

    /**
     * Isometric camera, with an adjustable Y rotation and zoom.
     * Coordinates are the actual GUI box boundaries in scaled GUI pixels.
     */
    public static void draw(GuiGraphics gui, Model model, int left, int top,
                            int width, int height, float angle, float zoom) {
        if(model==null||model.blocks().isEmpty()||width<20||height<20)return;
        var minecraft=Minecraft.getInstance();
        var pose=gui.pose();

        // Fit the diagonal bounding cube, not only the footprint. A tall
        // tower must not crop its spire and wide gates need room on both sides.
        double horizontal=(model.width()+model.length())*.75;
        double vertical=model.height()+horizontal*.50;
        float scale=(float)Math.min((width-18)/Math.max(1,horizontal),
            (height-14)/Math.max(1,vertical));
        scale=Math.max(.25f,Math.min(7.0f,scale*zoom));
        pose.pushPose();
        // The normal Minecraft GUI projection can render 3D block models in
        // this translated region without a framebuffer or an image download.
        pose.translate(left+width/2.0,top+height/2.0,180);
        pose.scale(scale,-scale,scale);
        pose.mulPose(Axis.XP.rotationDegrees(27));
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-model.width()/2.0,-model.height()/2.0,-model.length()/2.0);

        var renderer=minecraft.getBlockRenderer();
        var buffers=minecraft.renderBuffers().bufferSource();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1,1,1,1);
        // World-preview packet already limits the exterior to 2400 cells.
        // Rendering the actual block states also preserves slabs/stairs details.
        for(var cell:model.blocks()){
            pose.pushPose();
            pose.translate(cell.x(),cell.y(),cell.z());
            renderer.renderSingleBlock(cell.state(),pose,buffers,
                LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
        pose.popPose();
        RenderSystem.setShaderColor(1,1,1,1);
    }
}
