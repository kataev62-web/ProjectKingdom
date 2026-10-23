package ru.projectkingdom.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.npc.KingdomNpcAppearanceCatalog;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Alpha mesh renderer: leaves vanilla fallback on missing/corrupt assets. */
@Mod.EventBusSubscriber(modid="kingdomnpc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomFemaleMeshRenderer {
    private static final Map<UUID,String> ASSIGNED=new HashMap<>();
    private static final Map<String,Mesh> CACHE=new HashMap<>();
    private static final int MAGIC=0x504B4D48;
    private static final int GROUPS=6;
    private KingdomFemaleMeshRenderer(){}
    public static void sync(UUID uuid,String id) {
        if (KingdomNpcAppearanceCatalog.MODELS.contains(id)) ASSIGNED.put(uuid,id);
        else ASSIGNED.remove(uuid);
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ASSIGNED.clear();
        CACHE.clear();
    }
    private static String appearance(Villager npc){
        var assigned=ASSIGNED.get(npc.getUUID());
        if(assigned!=null)return assigned;
        if(npc.getCustomName()==null)return null;
        var name=npc.getCustomName().getString();
        if(!name.startsWith("PKNPC:"))return null;
        var candidate=name.substring(6);
        return KingdomNpcAppearanceCatalog.MODELS.contains(candidate)?candidate:null;
    }
    @SubscribeEvent public static void render(RenderLivingEvent.Pre<?,?> event) {
        if(!(event.getEntity() instanceof Villager npc) || npc.isInvisible())return;
        var id=appearance(npc);
        if(id==null)return;
        Mesh mesh=CACHE.get(id);
        if(mesh==null){
            mesh=load(id);
            if(mesh==null)return;
            CACHE.put(id,mesh);
        }
        PoseStack stack=event.getPoseStack();
        var buffers=event.getMultiBufferSource();
        var texture=new ResourceLocation("kingdomnpc","textures/entity/female_v11/"+id+".png");
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        stack.pushPose();
        float yaw=Mth.rotLerp(event.getPartialTick(),npc.yBodyRotO,npc.yBodyRot);
        stack.mulPose(Axis.YP.rotationDegrees(180f-yaw));
        float movement=Math.min(1f,npc.walkAnimation.speed(event.getPartialTick()));
        float phase=npc.walkAnimation.position(event.getPartialTick())*0.6662f;
        float swing=Mth.sin(phase)*0.48f*movement;
        for(int group=0;group<GROUPS;group++){
            stack.pushPose();
            float rotate=switch(group){case 2,5->swing;case 3,4->-swing;default->0;};
            if(rotate!=0){
                float[] pivot=mesh.pivots[group];
                stack.translate(pivot[0],pivot[1],pivot[2]);
                stack.mulPose(Axis.XP.rotation(rotate));
                stack.translate(-pivot[0],-pivot[1],-pivot[2]);
            }
            var pose=stack.last();
            for(float[] v:mesh.vertices[group]){
                out.vertex(pose.pose(),v[0],v[1],v[2])
                    .color(255,255,255,255)
                    .uv(v[6],v[7])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(event.getPackedLight())
                    .normal(pose.normal(),v[3],v[4],v[5])
                    .endVertex();
            }
            stack.popPose();
        }
        stack.popPose();
        if(npc.isCustomNameVisible()&&npc.getCustomName()!=null){
            stack.pushPose();
            stack.translate(0,npc.getBbHeight()+0.48,0);
            stack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            stack.scale(-0.025f,-0.025f,0.025f);
            var font=Minecraft.getInstance().font;
            var label=npc.getCustomName();
            font.drawInBatch(label,-font.width(label)/2f,0,0xffffffff,false,stack.last().pose(),
                    buffers,Font.DisplayMode.NORMAL,0,event.getPackedLight());
            stack.popPose();
        }
        event.setCanceled(true);
    }
    private static Mesh load(String id){
        var key=new ResourceLocation("kingdomnpc","models/entity/female_v11/"+id+".pkmesh.gz");
        var resource=Minecraft.getInstance().getResourceManager().getResource(key);
        if(resource.isEmpty())return null;
        try(var compressed=new GZIPInputStream(resource.get().open());var data=new DataInputStream(compressed)){
            if(data.readInt()!=MAGIC||data.readInt()!=1||data.readInt()!=GROUPS)return null;
            var mesh=new Mesh();
            for(int group=0;group<GROUPS;group++){
                for(int axis=0;axis<3;axis++){
                    var p=data.readFloat();
                    if(!Float.isFinite(p))return null;
                    mesh.pivots[group][axis]=p;
                }
                int count=data.readInt();
                if(count<0||count>120000||count%4!=0)return null;
                mesh.vertices[group]=new float[count][8];
                for(var v:mesh.vertices[group])for(int i=0;i<8;i++){
                    var n=data.readFloat();
                    if(!Float.isFinite(n))return null;
                    v[i]=n;
                }
            }
            return data.read()==-1?mesh:null;
        }catch(IOException|RuntimeException ex){return null;}
    }
    private static final class Mesh{
        final float[][][] vertices=new float[GROUPS][][];
        final float[][] pivots=new float[GROUPS][3];
    }
}
