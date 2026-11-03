package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomArchitectSessions;
import ru.projectkingdom.npc.KingdomConstructionPlans;
import ru.projectkingdom.npc.KingdomDesignEngine;
import ru.projectkingdom.npc.KingdomSchematicLibrary;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Authorization boundary and immutable proposal queue for assistant previews. */
public final class AssistantPreviewService {
    public enum Authorization {
        ACCEPTED,
        INVALID_PACKET,
        STALE_TOKEN,
        WRONG_OWNER,
        WRONG_ASSISTANT,
        NOT_ADMIN,
        EXPIRED,
        DIMENSION_CHANGED,
        OUT_OF_RANGE,
        INVALID_ROTATION
    }

    public enum QueueResult {
        QUEUED, NOT_ADMIN, NOT_OWNER, WRONG_ASSISTANT, DIMENSION_CHANGED, INVALID_DESIGN
    }

    public record SessionProof(
            UUID token,
            UUID owner,
            UUID assistant,
            String dimension,
            BlockPos anchor,
            long expires
    ) {}

    public record DecisionAttempt(
            UUID token,
            UUID actor,
            UUID savedAssistant,
            int permissionLevel,
            String dimension,
            BlockPos origin,
            int rotation,
            long gameTime
    ) {
        public DecisionAttempt withToken(UUID value) {
            return new DecisionAttempt(value, actor, savedAssistant, permissionLevel,
                    dimension, origin, rotation, gameTime);
        }
        public DecisionAttempt withActor(UUID value) {
            return new DecisionAttempt(token, value, savedAssistant, permissionLevel,
                    dimension, origin, rotation, gameTime);
        }
        public DecisionAttempt withSavedAssistant(UUID value) {
            return new DecisionAttempt(token, actor, value, permissionLevel,
                    dimension, origin, rotation, gameTime);
        }
        public DecisionAttempt withPermissionLevel(int value) {
            return new DecisionAttempt(token, actor, savedAssistant, value,
                    dimension, origin, rotation, gameTime);
        }
        public DecisionAttempt withDimension(String value) {
            return new DecisionAttempt(token, actor, savedAssistant, permissionLevel,
                    value, origin, rotation, gameTime);
        }
        public DecisionAttempt withOrigin(BlockPos value) {
            return new DecisionAttempt(token, actor, savedAssistant, permissionLevel,
                    dimension, value, rotation, gameTime);
        }
        public DecisionAttempt withRotation(int value) {
            return new DecisionAttempt(token, actor, savedAssistant, permissionLevel,
                    dimension, origin, value, gameTime);
        }
        public DecisionAttempt withGameTime(long value) {
            return new DecisionAttempt(token, actor, savedAssistant, permissionLevel,
                    dimension, origin, rotation, value);
        }
    }

    public record BuildProposal(
            UUID owner,
            UUID assistant,
            String dimension,
            String designKey,
            BlockPos origin,
            KingdomDesignEngine.Design design
    ) {
        public BuildProposal {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(assistant, "assistant");
            dimension = requireText(dimension, "dimension");
            designKey = requireText(designKey, "designKey");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            KingdomDesignEngine.Design source = Objects.requireNonNull(design, "design");
            design = new KingdomDesignEngine.Design(source.name(), source.width(), source.length(),
                    source.height(), List.copyOf(source.blocks()));
        }
    }

    private static final Map<MinecraftServer, Map<UUID, BuildProposal>> PROPOSALS =
            new WeakHashMap<>();

    private AssistantPreviewService() {}

    public static boolean begin(
            ServerPlayer owner, UUID assistantId, String requestedDesign, BlockPos anchor) {
        if (owner == null || owner.getServer() == null || requestedDesign == null) return false;
        String designKey = prepareDesignKey(owner.getServer(), requestedDesign.trim());
        return designKey != null
                && KingdomArchitectSessions.beginAssistant(owner, assistantId, designKey, anchor);
    }

    public static Authorization authorizeAndQueue(
            SessionProof session, DecisionAttempt attempt, Runnable queueAction) {
        if (session == null || attempt == null || queueAction == null
                || session.token() == null || session.owner() == null
                || session.assistant() == null || session.dimension() == null
                || session.anchor() == null || attempt.token() == null
                || attempt.actor() == null || attempt.savedAssistant() == null
                || attempt.dimension() == null || attempt.origin() == null)
            return Authorization.INVALID_PACKET;
        if (!session.token().equals(attempt.token())) return Authorization.STALE_TOKEN;
        if (!session.owner().equals(attempt.actor())) return Authorization.WRONG_OWNER;
        if (!session.assistant().equals(attempt.savedAssistant()))
            return Authorization.WRONG_ASSISTANT;
        if (attempt.permissionLevel() < 4) return Authorization.NOT_ADMIN;
        if (session.expires() < attempt.gameTime()) return Authorization.EXPIRED;
        if (!session.dimension().equals(attempt.dimension()))
            return Authorization.DIMENSION_CHANGED;
        long dx = (long) attempt.origin().getX() - session.anchor().getX();
        long dy = (long) attempt.origin().getY() - session.anchor().getY();
        long dz = (long) attempt.origin().getZ() - session.anchor().getZ();
        if (Math.abs(dx) > 80 || Math.abs(dy) > 32 || Math.abs(dz) > 80)
            return Authorization.OUT_OF_RANGE;
        if (attempt.rotation() < 0 || attempt.rotation() > 3)
            return Authorization.INVALID_ROTATION;
        queueAction.run();
        return Authorization.ACCEPTED;
    }

    public static QueueResult queue(
            ServerPlayer owner, UUID assistantId, String designKey, BlockPos origin) {
        if (owner == null || owner.getServer() == null || !owner.hasPermissions(4))
            return QueueResult.NOT_ADMIN;
        var state = KingdomAssistantData.get(owner.getServer()).ownedBy(owner.getUUID());
        if (state.isEmpty()) return QueueResult.NOT_OWNER;
        if (assistantId == null || !assistantId.equals(state.get().entityId()))
            return QueueResult.WRONG_ASSISTANT;
        String dimension = owner.serverLevel().dimension().location().toString();
        if (!dimension.equals(state.get().dimension())) return QueueResult.DIMENSION_CHANGED;
        KingdomDesignEngine.Design design = KingdomConstructionPlans.resolve(
                owner.getServer(), designKey);
        if (origin == null || design == null || design.blocks().isEmpty())
            return QueueResult.INVALID_DESIGN;
        BuildProposal proposal = new BuildProposal(owner.getUUID(), assistantId, dimension,
                designKey, origin, design);
        PROPOSALS.computeIfAbsent(owner.getServer(), unused -> new HashMap<>())
                .put(owner.getUUID(), proposal);
        return QueueResult.QUEUED;
    }

    public static Optional<BuildProposal> pending(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return Optional.empty();
        Map<UUID, BuildProposal> proposals = PROPOSALS.get(server);
        return proposals == null ? Optional.empty() : Optional.ofNullable(proposals.get(owner));
    }

    public static Optional<BuildProposal> consume(
            MinecraftServer server, UUID owner, UUID assistant) {
        Optional<BuildProposal> proposal = pending(server, owner);
        if (proposal.isEmpty() || !proposal.get().assistant().equals(assistant))
            return Optional.empty();
        PROPOSALS.get(server).remove(owner);
        return proposal;
    }

    private static String prepareDesignKey(MinecraftServer server, String requested) {
        if (requested.isEmpty()) return null;
        if (KingdomConstructionPlans.resolve(server, requested) != null) return requested;
        if (!KingdomSchematicLibrary.validName(requested)) return null;
        try {
            return KingdomSchematicLibrary.freeze(server, requested);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field);
        return normalized;
    }
}
