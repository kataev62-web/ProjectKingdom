package ru.projectkingdom.enterprise;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import ru.projectkingdom.npc.KingdomNpcMod;

public final class KingdomEnterpriseRegistry {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,KingdomNpcMod.MOD_ID);
    private static final Map<EnterpriseKind,RegistryObject<Block>> BLOCK_BY_KIND=new EnumMap<>(EnterpriseKind.class);

    public static final RegistryObject<Block> FARM=block("farm_core",EnterpriseKind.FARM);
    public static final RegistryObject<Block> MINE=block("mine_core",EnterpriseKind.MINE);
    public static final RegistryObject<Block> LUMBER=block("lumber_core",EnterpriseKind.LUMBER);
    public static final RegistryObject<Block> SMITHY=block("smithy_core",EnterpriseKind.SMITHY);
    public static final RegistryObject<Block> BAKERY=block("bakery_core",EnterpriseKind.BAKERY);
    public static final RegistryObject<Block> POTTERY=block("pottery_core",EnterpriseKind.POTTERY);
    public static final RegistryObject<Block> CONSTRUCTION=block("construction_core",EnterpriseKind.CONSTRUCTION);
    public static final RegistryObject<Block> COURIER=block("courier_core",EnterpriseKind.COURIER);
    public static final RegistryObject<Block> HOTEL=block("hotel_core",EnterpriseKind.HOTEL);
    public static final RegistryObject<Block> TAVERN=block("tavern_core",EnterpriseKind.TAVERN);

    public static final RegistryObject<Item> FARM_ITEM=item("farm_core",FARM);
    public static final RegistryObject<Item> MINE_ITEM=item("mine_core",MINE);
    public static final RegistryObject<Item> LUMBER_ITEM=item("lumber_core",LUMBER);
    public static final RegistryObject<Item> SMITHY_ITEM=item("smithy_core",SMITHY);
    public static final RegistryObject<Item> BAKERY_ITEM=item("bakery_core",BAKERY);
    public static final RegistryObject<Item> POTTERY_ITEM=item("pottery_core",POTTERY);
    public static final RegistryObject<Item> CONSTRUCTION_ITEM=item("construction_core",CONSTRUCTION);
    public static final RegistryObject<Item> COURIER_ITEM=item("courier_core",COURIER);
    public static final RegistryObject<Item> HOTEL_ITEM=item("hotel_core",HOTEL);
    public static final RegistryObject<Item> TAVERN_ITEM=item("tavern_core",TAVERN);

    public static final RegistryObject<BlockEntityType<EnterpriseCoreBlockEntity>> CORE_ENTITY=ENTITIES.register("enterprise_core",()->
            BlockEntityType.Builder.of(EnterpriseCoreBlockEntity::new,FARM.get(),MINE.get(),LUMBER.get(),SMITHY.get(),
                    BAKERY.get(),POTTERY.get(),CONSTRUCTION.get(),COURIER.get(),HOTEL.get(),TAVERN.get()).build(null));
    public static final RegistryObject<MenuType<EnterpriseCoreMenu>> CORE_MENU=MENUS.register("enterprise_core",()->IForgeMenuType.create(EnterpriseCoreMenu::new));
    private KingdomEnterpriseRegistry(){}
    private static RegistryObject<Block> block(String id,EnterpriseKind kind){
        RegistryObject<Block> value=BLOCKS.register(id,()->new EnterpriseCoreBlock(kind));BLOCK_BY_KIND.put(kind,value);return value;
    }
    private static RegistryObject<Item> item(String id,RegistryObject<Block> block){return ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties()));}
    public static RegistryObject<Block> block(EnterpriseKind kind){return BLOCK_BY_KIND.get(kind);}
    public static void register(){var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);}
}
