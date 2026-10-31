package ru.projectkingdom.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * Managed Kingdom villager: Minecraft PlayerModel + standard player 64x64 skin UV.
 * Unmanaged villagers use original VillagerRenderer and retain all vanilla features.
 * Entity identity, NBT, trades, goals, pathing and server AI are NOT changed.
 */
public final class KingdomPlayerNpcRenderer {
    public static final ModelLayerLocation OUTFIT_LAYER = new ModelLayerLocation(
            new ResourceLocation("kingdomnpc", "player_npc_clothes"), "main");

    private KingdomPlayerNpcRenderer() {}

    public static LayerDefinition createOutfitLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // Humanoid/PlayerModel uses 0..24 texture-space Y (rather than VillagerModel's 0..24).
        // Small blouse volume covers adult torso, not skin. No exposed body parts.
        root.addOrReplaceChild("blouse",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.75f, 3.0f, -3.40f, 3.5f, 3.3f, 1.55f)
                .texOffs(0, 0).addBox(0.25f, 3.0f, -3.40f, 3.5f, 3.3f, 1.55f),
            PartPose.ZERO);
        root.addOrReplaceChild("waist",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.12f, 10.6f, -2.35f, 8.24f, 1.9f, 4.7f),
            PartPose.ZERO);
        // Three gradual skirt segments, one union around the two animated legs.
        // Simple cuboids, so dozens of NPCs incur little extra geometry.
        root.addOrReplaceChild("skirt_upper",
            CubeListBuilder.create().texOffs(0, 26)
                .addBox(-4.05f, 12.2f, -2.70f, 8.1f, 3.2f, 5.4f),
            PartPose.ZERO);
        root.addOrReplaceChild("skirt_middle",
            CubeListBuilder.create().texOffs(0, 36)
                .addBox(-4.65f, 15.4f, -3.12f, 9.3f, 4.0f, 6.24f),
            PartPose.ZERO);
        root.addOrReplaceChild("skirt_hem",
            CubeListBuilder.create().texOffs(0, 50)
                .addBox(-5.05f, 19.4f, -3.55f, 10.1f, 3.2f, 7.1f),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The global Villager renderer switches *only* already-synced Kingdom worker UUIDs. */
    public static final class KingdomVillagerRenderer extends VillagerRenderer {
        private final HumanRenderer human;

        public KingdomVillagerRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.human = new HumanRenderer(context);
        }

        @Override
        public void render(Villager villager, float yaw, float partialTick, PoseStack stack,
                           MultiBufferSource buffers, int light) {
            if (KingdomFemaleOutfitClient.isHuman(villager.getUUID())) {
                human.render(villager, yaw, partialTick, stack, buffers, light);
            } else {
                super.render(villager, yaw, partialTick, stack, buffers, light);
            }
        }
    }

    /** Renders the true vanilla PlayerModel geometry/animations, not the VillagerModel. */
    private static final class HumanRenderer extends MobRenderer<Villager, PlayerModel<Villager>> {
        private HumanRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
            addLayer(new SkirtAndBustLayer(this, context.bakeLayer(OUTFIT_LAYER)));
        }

        @Override
        public ResourceLocation getTextureLocation(Villager villager) {
            return KingdomFemaleOutfitClient.skin(villager.getUUID());
        }

        @Override
        protected void scale(Villager villager, PoseStack stack, float partialTick) {
            // Normal adult human height; babies are handled by vanilla renderer young scaling.
            stack.scale(0.94f, 0.94f, 0.94f);
        }
    }

    private static final class SkirtAndBustLayer extends RenderLayer<Villager, PlayerModel<Villager>> {
        private final ModelPart clothes;

        private SkirtAndBustLayer(RenderLayerParent<Villager, PlayerModel<Villager>> parent,
                                 ModelPart model) {
            super(parent);
            clothes = model;
        }

        @Override
        public void render(PoseStack stack, MultiBufferSource buffers, int light,
                           Villager villager, float limbSwing, float limbAmount,
                           float partialTick, float ageTicks, float yaw, float pitch) {
            // 2 = adult female. No skirt/bust for men, children or unregistered vanilla NPCs.
            if (KingdomFemaleOutfitClient.appearance(villager.getUUID()) != 2
                    || villager.isInvisible() || villager.isBaby()) return;
            clothes.render(stack,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(
                            KingdomFemaleOutfitClient.outfit(villager.getUUID()))),
                    light, OverlayTexture.NO_OVERLAY);
        }
    }
}
