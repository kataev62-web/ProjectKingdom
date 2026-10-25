package ru.projectkingdom.core;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.projectkingdom.npc.KingdomNpcMod;
import ru.projectkingdom.economy.KingdomEconomyRegistry;
import ru.projectkingdom.economy.MoneyPurseScreen;
import ru.projectkingdom.enterprise.EnterpriseCoreScreen;
import ru.projectkingdom.enterprise.KingdomEnterpriseRegistry;
import ru.projectkingdom.administration.AdministrativeCoreScreen;
import ru.projectkingdom.administration.KingdomAdminEnterpriseScreen;
import ru.projectkingdom.administration.KingdomAdministrationRegistry;

@Mod.EventBusSubscriber(modid = KingdomNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KingdomBookClient {
    private KingdomBookClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(KingdomBookRegistry.PLAYER_BOOK_MENU.get(), KingdomBookScreen::new));
        event.enqueueWork(() -> MenuScreens.register(KingdomEconomyRegistry.PURSE_MENU.get(), MoneyPurseScreen::new));
        event.enqueueWork(() -> MenuScreens.register(KingdomEnterpriseRegistry.CORE_MENU.get(), EnterpriseCoreScreen::new));
        event.enqueueWork(() -> MenuScreens.register(KingdomAdministrationRegistry.CORE_MENU.get(), AdministrativeCoreScreen::new));
        event.enqueueWork(() -> MenuScreens.register(KingdomAdministrationRegistry.ADMIN_ENTERPRISE_MENU.get(), KingdomAdminEnterpriseScreen::new));
    }
}
