package ru.projectkingdom.economy;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.projectkingdom.npc.KingdomNpcMod;

public final class KingdomEconomyRegistry {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, KingdomNpcMod.MOD_ID);
    public static final RegistryObject<MenuType<MoneyPurseMenu>> PURSE_MENU = MENUS.register(
            "money_purse", () -> IForgeMenuType.create(MoneyPurseMenu::new));

    private KingdomEconomyRegistry() {}

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
