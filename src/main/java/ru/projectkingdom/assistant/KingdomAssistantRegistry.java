package ru.projectkingdom.assistant;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.projectkingdom.npc.KingdomNpcMod;

import java.util.UUID;

/** Forge registration and owner-only lifecycle for the personal assistant. */
public final class KingdomAssistantRegistry {
    public static final String REGISTRY_NAME = "kingdom_assistant";
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomNpcMod.MOD_ID);

    public static final RegistryObject<EntityType<KingdomAssistantEntity>> ASSISTANT =
            ENTITIES.register(REGISTRY_NAME, () -> EntityType.Builder
                    .<KingdomAssistantEntity>of(KingdomAssistantEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(KingdomNpcMod.MOD_ID + ":" + REGISTRY_NAME));

    public enum SpawnResult { CREATED, ALREADY_EXISTS, NOT_ADMIN }

    private KingdomAssistantRegistry() {}

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
        eventBus.addListener(KingdomAssistantRegistry::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(ASSISTANT.get(), Villager.createAttributes().build());
    }

    public static SpawnResult spawnFor(ServerPlayer owner) {
        if (owner == null || !owner.hasPermissions(4)) return SpawnResult.NOT_ADMIN;
        MinecraftServer server = owner.getServer();
        if (server == null) return SpawnResult.NOT_ADMIN;
        KingdomAssistantData data = KingdomAssistantData.get(server);
        var saved = data.ownedBy(owner.getUUID());
        if (saved.isPresent()) {
            KingdomAssistantData.OwnerState state = saved.get();
            Entity loaded = findLoaded(server, state.entityId());
            if (loaded instanceof KingdomAssistantEntity assistant) {
                assistant.setOwnerId(owner.getUUID());
                assistant.setAssistantMode(state.mode());
            }
            // An unloaded saved entity is not duplicated and its chunk is not forced.
            return SpawnResult.ALREADY_EXISTS;
        }

        ServerLevel level = owner.serverLevel();
        KingdomAssistantEntity assistant = ASSISTANT.get().create(level);
        if (assistant == null) throw new IllegalStateException("Cannot create Kingdom assistant entity");
        assistant.moveTo(owner.getX() + 1.0D, owner.getY(), owner.getZ() + 1.0D,
                owner.getYRot(), 0.0F);
        assistant.setOwnerId(owner.getUUID());
        assistant.setAssistantMode(KingdomAssistantData.Mode.FOLLOW);
        if (!level.addFreshEntity(assistant)) {
            assistant.discard();
            throw new IllegalStateException("Cannot add Kingdom assistant entity to the world");
        }
        String dimension = level.dimension().location().toString();
        if (!data.bind(owner.getUUID(), assistant.getUUID(), dimension)) {
            assistant.discard();
            return SpawnResult.ALREADY_EXISTS;
        }
        return SpawnResult.CREATED;
    }

    private static Entity findLoaded(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) return entity;
        }
        return null;
    }
}
