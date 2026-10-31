package ru.projectkingdom.npc.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Visual state sent by the server. A Kingdom worker stays an ordinary Villager
 * entity; the registered villager renderer delegates managed NPCs to PlayerModel.
 */
public final class KingdomFemaleOutfitClient {
    private static final Map<UUID, Byte> APPEARANCE = new HashMap<>();
    private static final ResourceLocation[] FEMALE_SKINS = new ResourceLocation[4];
    private static final ResourceLocation[] MALE_SKINS = new ResourceLocation[4];
    private static final ResourceLocation[] OUTFITS = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) {
            FEMALE_SKINS[i] = new ResourceLocation("kingdomnpc", "textures/entity/player_female_" + i + ".png");
            MALE_SKINS[i] = new ResourceLocation("kingdomnpc", "textures/entity/player_male_" + i + ".png");
            OUTFITS[i] = new ResourceLocation("kingdomnpc", "textures/entity/female_outfit_" + i + ".png");
        }
    }

    private KingdomFemaleOutfitClient() {}

    public static void apply(UUID entityId, byte appearance) {
        if (appearance <= 0 || appearance > 3) APPEARANCE.remove(entityId);
        else APPEARANCE.put(entityId, appearance);
    }

    public static byte appearance(UUID uuid) {
        return APPEARANCE.getOrDefault(uuid, (byte)0);
    }

    public static boolean isHuman(UUID uuid) {
        return appearance(uuid) != 0;
    }

    public static int variant(UUID uuid) {
        return Math.floorMod(uuid.hashCode(), 4);
    }

    public static ResourceLocation skin(UUID uuid) {
        byte kind = appearance(uuid);
        return (kind == 2 || kind == 3 ? FEMALE_SKINS : MALE_SKINS)[variant(uuid)];
    }

    public static ResourceLocation outfit(UUID uuid) { return OUTFITS[variant(uuid)]; }

    @Mod.EventBusSubscriber(modid="kingdomnpc", bus=Mod.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
    public static final class RendererRegistration {
        private RendererRegistration() {}

        @SubscribeEvent
        public static void replaceVillagerRenderer(EntityRenderersEvent.RegisterRenderers event) {
            // Replaces the visual renderer for EntityType.VILLAGER, not the entity.
            // Ordinary villagers continue to use the original vanilla renderer branch.
            event.registerEntityRenderer(EntityType.VILLAGER,
                    KingdomPlayerNpcRenderer.KingdomVillagerRenderer::new);
        }

        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(KingdomPlayerNpcRenderer.OUTFIT_LAYER,
                    KingdomPlayerNpcRenderer::createOutfitLayer);
        }
    }

    @Mod.EventBusSubscriber(modid="kingdomnpc", bus=Mod.EventBusSubscriber.Bus.FORGE, value=Dist.CLIENT)
    public static final class SessionEvents {
        private SessionEvents() {}
        @SubscribeEvent
        public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            APPEARANCE.clear();
        }
    }
}
