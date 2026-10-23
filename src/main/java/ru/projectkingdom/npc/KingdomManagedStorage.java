package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Dedicated storage: automation capability is exposed only through the guarded block entity. */
public final class KingdomManagedStorage {
    private KingdomManagedStorage() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomNpcMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, KingdomNpcMod.MOD_ID);
    public static final RegistryObject<Block> STORAGE = BLOCKS.register("managed_storage", StorageBlock::new);
    public static final RegistryObject<Item> STORAGE_ITEM = ITEMS.register("managed_storage", () -> new BlockItem(STORAGE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<KingdomManagedStorageEntity>> STORAGE_ENTITY =
            ENTITIES.register("managed_storage", () -> BlockEntityType.Builder.of(KingdomManagedStorageEntity::new, STORAGE.get()).build(null));
    public static void register() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
    }
    private static final class StorageBlock extends Block implements EntityBlock {
        private StorageBlock() { super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0f)); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new KingdomManagedStorageEntity(pos, state);
        }

        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                          LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level.isClientSide || !(placer instanceof ServerPlayer player)
                    || !(level.getBlockEntity(pos) instanceof KingdomManagedStorageEntity storage)) return;
            if (KingdomTerritories.get(player.getServer()).canUse(player.serverLevel(), pos, player.getUUID())) {
                storage.bind(player.getUUID());
            }
        }

        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !(level.getBlockEntity(pos) instanceof KingdomManagedStorageEntity storage)) {
                return InteractionResult.PASS;
            }
            if (storage.owner() == null && KingdomTerritories.get(serverPlayer.getServer())
                    .canUse(serverPlayer.serverLevel(), pos, serverPlayer.getUUID())) {
                storage.bind(serverPlayer.getUUID());
            }
            if (!storage.canOpen(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal("[Project Kingdom] Это хранилище вам недоступно."));
                return InteractionResult.CONSUME;
            }
            player.openMenu(storage);
            return InteractionResult.CONSUME;
        }

        @Override public void onRemove(BlockState state, Level level, BlockPos pos,
                                       BlockState replacement, boolean moved) {
            if (!state.is(replacement.getBlock())) {
                if (level.getBlockEntity(pos) instanceof KingdomManagedStorageEntity storage) {
                    Containers.dropContents(level, pos, storage);
                }
                super.onRemove(state, level, pos, replacement, moved);
            }
        }
    }
}
