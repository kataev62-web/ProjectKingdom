package ru.projectkingdom.npc;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Craftable construction tools inspired by Ancient Warfare's drafting/building loop.
 * Independent implementation for Forge 1.20.1; no Ancient Warfare runtime dependency.
 */
public final class KingdomConstructionRegistry {
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomNpcMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, KingdomNpcMod.MOD_ID);

    public static final RegistryObject<Block> DRAFTING_TABLE =
        BLOCKS.register("drafting_table",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.LECTERN).strength(2.5F)));

    public static final RegistryObject<Item> DRAFTING_TABLE_ITEM =
        ITEMS.register("drafting_table",
            () -> new BlockItem(DRAFTING_TABLE.get(), new Item.Properties()));

    /** One unbound blueprint is consumed when a survival player registers a construction site. */
    public static final RegistryObject<Item> BUILDING_BLUEPRINT =
        ITEMS.register("building_blueprint", () -> new Item(new Item.Properties().stacksTo(16)));

    /** A manual builder contributes one real block per click from the project material depot. */
    public static final RegistryObject<Item> BUILDERS_HAMMER =
        ITEMS.register("builders_hammer", () -> new Item(new Item.Properties().durability(384)));

    private KingdomConstructionRegistry() {}

    public static void register() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
