package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomForgeHandlerRegistrationTest {
    @Test void subscribedMethodsAcceptExactlyOneForgeEvent() {
        for (var method : KingdomNpcMod.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(SubscribeEvent.class)) continue;
            assertEquals(1, method.getParameterCount(), method.toString());
            assertTrue(Event.class.isAssignableFrom(method.getParameterTypes()[0]), method.toString());
        }
    }
    @Test void realTickIsSubscribedAndHelperIsNot() throws Exception {
        assertNotNull(KingdomNpcMod.class.getDeclaredMethod("onServerTick", TickEvent.ServerTickEvent.class)
            .getAnnotation(SubscribeEvent.class));
        assertNull(KingdomNpcMod.class.getDeclaredMethod("arbitrateResidentNavigation",
            net.minecraft.server.MinecraftServer.class).getAnnotation(SubscribeEvent.class));
    }
}
