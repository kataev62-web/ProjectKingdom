package ru.projectkingdom.npc;

import java.util.Comparator;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.projectkingdom.administration.KingdomAdministrationRegistry;

/**
 * One creative-inventory tab for every placeable Project Kingdom block.
 *
 * Browse the live block registry rather than manually listing module entries:
 * administration, enterprises, settlement, construction, storage and any
 * future Kingdom modules are all included automatically once registered.
 * Blocks without an item representation cannot be picked up from inventory.
 */
public final class KingdomCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KingdomNpcMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BLOCKS = TABS.register("kingdom_blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kingdomnpc.blocks"))
                    .icon(() -> new ItemStack(KingdomAdministrationRegistry.TOWN_HALL_ITEM.get()))
                    .displayItems((parameters, output) -> ForgeRegistries.BLOCKS.getValues().stream()
                            .filter(block -> {
                                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                                return id != null && KingdomNpcMod.MOD_ID.equals(id.getNamespace())
                                        && !"settlement_charter".equals(id.getPath());
                            })
                            .sorted(Comparator.comparing(block -> ForgeRegistries.BLOCKS.getKey(block).getPath()))
                            .map(block -> block.asItem())
                            .filter(item -> item != Items.AIR)
                            .distinct()
                            .forEach(item -> output.accept(new ItemStack(item))))
                    .build());

    private KingdomCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
