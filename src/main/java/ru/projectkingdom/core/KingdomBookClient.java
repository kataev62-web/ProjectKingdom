package ru.projectkingdom.core;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.economy.KingdomEconomyRegistry;
import ru.projectkingdom.economy.MoneyPurseScreen;

@Mod.EventBusSubscriber(modid = KingdomNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KingdomBookClient {
    private KingdomBookClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(KingdomBookRegistry.PLAYER_BOOK_MENU.get(), KingdomBookScreen::new));
        event.enqueueWork(() -> MenuScreens.register(KingdomEconomyRegistry.PURSE_MENU.get(), MoneyPurseScreen::new));
    }
}
