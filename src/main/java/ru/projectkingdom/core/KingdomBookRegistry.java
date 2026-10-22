package ru.projectkingdom.core;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.projectkingdom.npc.KingdomNpcMod;

public final class KingdomBookRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomNpcMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, KingdomNpcMod.MOD_ID);

    public static final RegistryObject<Item> PLAYER_BOOK = ITEMS.register("player_book",
            () -> new KingdomBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<MenuType<KingdomBookMenu>> PLAYER_BOOK_MENU = MENUS.register("player_book",
            () -> IForgeMenuType.create(KingdomBookMenu::new));

    private KingdomBookRegistry() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        MENUS.register(bus);
    }
}
