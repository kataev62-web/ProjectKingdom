package ru.projectkingdom.administration;

import java.util.EnumMap;
import java.util.Map;
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

public final class KingdomAdministrationRegistry {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,KingdomNpcMod.MOD_ID);
    private static final Map<AdministrativeKind,RegistryObject<Block>> BLOCK_BY_KIND=new EnumMap<>(AdministrativeKind.class);

    public static final RegistryObject<Block> TOWN_HALL=block("town_hall_core",AdministrativeKind.TOWN_HALL);
    public static final RegistryObject<Block> TREASURY=block("treasury_core",AdministrativeKind.TREASURY);
    public static final RegistryObject<Block> BANK=block("bank_core",AdministrativeKind.BANK);
    public static final RegistryObject<Block> CENTRAL_WAREHOUSE=block("central_warehouse_core",AdministrativeKind.CENTRAL_WAREHOUSE);
    public static final RegistryObject<Block> CITY_MARKET=block("city_market_core",AdministrativeKind.CITY_MARKET);
    public static final RegistryObject<Block> LAND_OFFICE=block("land_office_core",AdministrativeKind.LAND_OFFICE);
    public static final RegistryObject<Block> COURT=block("court_core",AdministrativeKind.COURT);
    public static final RegistryObject<Block> PRISON=block("prison_core",AdministrativeKind.PRISON);
    public static final RegistryObject<Block> GUARD_ADMINISTRATION=block("guard_administration_core",AdministrativeKind.GUARD_ADMINISTRATION);

    public static final RegistryObject<Item> TOWN_HALL_ITEM=item("town_hall_core",TOWN_HALL);
    public static final RegistryObject<Item> TREASURY_ITEM=item("treasury_core",TREASURY);
    public static final RegistryObject<Item> BANK_ITEM=item("bank_core",BANK);
    public static final RegistryObject<Item> CENTRAL_WAREHOUSE_ITEM=item("central_warehouse_core",CENTRAL_WAREHOUSE);
    public static final RegistryObject<Item> CITY_MARKET_ITEM=item("city_market_core",CITY_MARKET);
    public static final RegistryObject<Item> LAND_OFFICE_ITEM=item("land_office_core",LAND_OFFICE);
    public static final RegistryObject<Item> COURT_ITEM=item("court_core",COURT);
    public static final RegistryObject<Item> PRISON_ITEM=item("prison_core",PRISON);
    public static final RegistryObject<Item> GUARD_ADMINISTRATION_ITEM=item("guard_administration_core",GUARD_ADMINISTRATION);

    public static final RegistryObject<BlockEntityType<AdministrativeCoreBlockEntity>> CORE_ENTITY=ENTITIES.register("administrative_core",()->
            BlockEntityType.Builder.of(AdministrativeCoreBlockEntity::new,TOWN_HALL.get(),TREASURY.get(),BANK.get(),CENTRAL_WAREHOUSE.get(),
                    CITY_MARKET.get(),LAND_OFFICE.get(),COURT.get(),PRISON.get(),GUARD_ADMINISTRATION.get()).build(null));
    public static final RegistryObject<MenuType<AdministrativeCoreMenu>> CORE_MENU=MENUS.register("administrative_core",()->IForgeMenuType.create(AdministrativeCoreMenu::new));
    private KingdomAdministrationRegistry(){}
    private static RegistryObject<Block> block(String id,AdministrativeKind kind){
        RegistryObject<Block> value=BLOCKS.register(id,()->new AdministrativeCoreBlock(kind));BLOCK_BY_KIND.put(kind,value);return value;
    }
    private static RegistryObject<Item> item(String id,RegistryObject<Block> block){return ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties()));}
    public static RegistryObject<Block> block(AdministrativeKind kind){return BLOCK_BY_KIND.get(kind);}
    public static void register(){var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);}
}
