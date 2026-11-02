package ru.projectkingdom.assistant.client;

import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.assistant.KingdomAssistantRegistry;
import ru.projectkingdom.npc.KingdomNpcMod;

/** First-version renderer: the dedicated entity intentionally uses vanilla villager visuals. */
@Mod.EventBusSubscriber(modid = KingdomNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KingdomAssistantClient {
    private KingdomAssistantClient() {}

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(KingdomAssistantRegistry.ASSISTANT.get(), VillagerRenderer::new);
    }
}
