package ru.projectkingdom.settlement;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.projectkingdom.npc.KingdomNpcMod;

public final class KingdomSettlementRegistry {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,KingdomNpcMod.MOD_ID);
    public static final RegistryObject<Block> CHARTER=BLOCKS.register("settlement_charter",KingdomSettlementCharterBlock::new);
    public static final RegistryObject<Item> CHARTER_ITEM=ITEMS.register("settlement_charter",()->new BlockItem(CHARTER.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<KingdomSettlementCharterEntity>> ENTITY=ENTITIES.register("settlement_charter",()->
            BlockEntityType.Builder.of(KingdomSettlementCharterEntity::new,CHARTER.get()).build(null));
    public static final RegistryObject<MenuType<KingdomSettlementMenu>> MENU=MENUS.register("settlement_charter",()->
            IForgeMenuType.create(KingdomSettlementMenu::new));
    private KingdomSettlementRegistry(){}
    public static void register(){
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);
    }
}
