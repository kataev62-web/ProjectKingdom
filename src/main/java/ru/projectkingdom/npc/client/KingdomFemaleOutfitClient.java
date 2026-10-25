package ru.projectkingdom.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.EntityModelLayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla Villager model + low-cost, textured blouse bust and long skirt.
 * Does not replace the entity renderer, face, vanilla animations or behavior.
 * Works only for server-approved adult female Kingdom NPCs.
 */
public final class KingdomFemaleOutfitClient {
    private static final EntityModelLayer OUTFIT_LAYER = new EntityModelLayer(
            new ResourceLocation("kingdomnpc", "female_outfit"), "main");
    private static final ResourceLocation CLOTH = new ResourceLocation(
            "kingdomnpc", "textures/entity/female_outfit.png");
    private static final Set<UUID> FEMALE = new HashSet<>();

    private KingdomFemaleOutfitClient() {}

    public static void apply(UUID entityId, boolean enabled) {
        if (enabled) FEMALE.add(entityId);
        else FEMALE.remove(entityId);
    }

    @Mod.EventBusSubscriber(modid="kingdomnpc", bus=Mod.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
    public static final class ModelRegistration {
        private ModelRegistration() {}

        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(OUTFIT_LAYER, KingdomFemaleOutfitClient::createLayer);
        }

        @SubscribeEvent
        public static void addToVillager(EntityRenderersEvent.AddLayers event) {
            if (event.getEntityRenderer(EntityType.VILLAGER) instanceof VillagerRenderer renderer) {
                renderer.addLayer(new OutfitLayer(renderer, event.getContext().bakeLayer(OUTFIT_LAYER)));
            }
        }
    }

    @Mod.EventBusSubscriber(modid="kingdomnpc", bus=Mod.EventBusSubscriber.Bus.FORGE, value=Dist.CLIENT)
    public static final class SessionEvents {
        private SessionEvents() {}

        @SubscribeEvent
        public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            FEMALE.clear();
        }
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();

        // White shirt sits over the front of the existing villager torso.
        // Flat-sided, modest volume: breast shape comes from geometry, not the texture.
        root.addOrReplaceChild("shirt_bust",
                CubeListBuilder.create()
                        .texOffs(0,0).addBox(-3.70f, 3.30f, -4.75f, 3.50f, 3.40f, 1.90f)
                        .texOffs(0,0).addBox( 0.20f, 3.30f, -4.75f, 3.50f, 3.40f, 1.90f),
                PartPose.ZERO);

        // Waist: slim connection between original villager body and the skirt.
        root.addOrReplaceChild("waist",
                CubeListBuilder.create().texOffs(0,16)
                        .addBox(-4.05f, 10.40f, -3.35f, 8.10f, 2.00f, 6.70f),
                PartPose.ZERO);

        // Tapered three-tier skirt: all parts are light Minecraft cuboids.
        // Separate front/back and side panels allow the vanilla legs to animate.
        root.addOrReplaceChild("skirt_front",
                CubeListBuilder.create()
                        .texOffs(0,33).addBox(-4.00f, 12.20f, -3.90f, 8.00f, 2.70f, 0.85f)
                        .texOffs(0,42).addBox(-4.75f, 14.90f, -4.45f, 9.50f, 4.10f, 0.85f)
                        .texOffs(0,52).addBox(-5.35f, 19.00f, -5.00f, 10.70f, 3.10f, 0.85f),
                PartPose.ZERO);
        root.addOrReplaceChild("skirt_back",
                CubeListBuilder.create()
                        .texOffs(0,33).addBox(-4.00f, 12.20f, 3.05f, 8.00f, 2.70f, 0.85f)
                        .texOffs(0,42).addBox(-4.75f, 14.90f, 3.60f, 9.50f, 4.10f, 0.85f)
                        .texOffs(0,52).addBox(-5.35f, 19.00f, 4.15f, 10.70f, 3.10f, 0.85f),
                PartPose.ZERO);
        root.addOrReplaceChild("skirt_left",
                CubeListBuilder.create()
                        .texOffs(34,33).addBox(3.30f, 12.20f, -3.30f, 0.85f, 2.70f, 6.60f)
                        .texOffs(34,42).addBox(4.00f, 14.90f, -3.95f, 0.85f, 4.10f, 7.90f)
                        .texOffs(34,52).addBox(4.70f, 19.00f, -4.65f, 0.85f, 3.10f, 9.30f),
                PartPose.ZERO);
        root.addOrReplaceChild("skirt_right",
                CubeListBuilder.create()
                        .texOffs(34,33).addBox(-4.15f, 12.20f, -3.30f, 0.85f, 2.70f, 6.60f)
                        .texOffs(34,42).addBox(-4.85f, 14.90f, -3.95f, 0.85f, 4.10f, 7.90f)
                        .texOffs(34,52).addBox(-5.55f, 19.00f, -4.65f, 0.85f, 3.10f, 9.30f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static final class OutfitLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
        private final ModelPart outfit;

        OutfitLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, ModelPart outfit) {
            super(parent);
            this.outfit = outfit;
        }

        @Override
        public void render(PoseStack stack, MultiBufferSource buffers, int light,
                Villager villager, float limbSwing, float limbAmount, float partialTick,
                float ageTicks, float yaw, float pitch) {
            if (!FEMALE.contains(villager.getUUID()) || villager.isInvisible() || villager.isBaby()) return;
            outfit.render(stack,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(CLOTH)),
                    light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }
}
