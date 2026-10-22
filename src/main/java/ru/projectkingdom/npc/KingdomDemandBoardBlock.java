package ru.projectkingdom.npc;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** First physical entry point: read-only board; fulfillment requires durable settlement integration. */
public final class KingdomDemandBoardBlock extends Block {
    private static final Map<MinecraftServer, DemandBoardCatalog> CATALOGS = new WeakHashMap<>();
    public static synchronized DemandBoardCatalog catalog(MinecraftServer server) {
        return CATALOGS.computeIfAbsent(server, ignored -> new DemandBoardCatalog());
    }
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomNpcMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomNpcMod.MOD_ID);
    public static final RegistryObject<Block> BOARD = BLOCKS.register("demand_board", KingdomDemandBoardBlock::new);
    public static final RegistryObject<Item> BOARD_ITEM = ITEMS.register("demand_board", () -> new BlockItem(BOARD.get(), new Item.Properties()));
    public static void register() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
    private KingdomDemandBoardBlock() { super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f)); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        DemandBoardCatalog catalog = catalog(serverPlayer.getServer());
        serverPlayer.sendSystemMessage(Component.literal("[Доска потребностей] Активных заявок: " + catalog.size()));
        for (var listing : catalog.page(0, 8)) {
            serverPlayer.sendSystemMessage(Component.literal(listing.key().item() + " — " + listing.remaining()
                    + " шт., до " + listing.maximumUnitCopper() + " медных монет/шт."));
        }
        serverPlayer.sendSystemMessage(Component.literal("Приёмка временно закрыта до подключения физической оплаты и постоянного журнала."));
        return InteractionResult.CONSUME;
    }
}
