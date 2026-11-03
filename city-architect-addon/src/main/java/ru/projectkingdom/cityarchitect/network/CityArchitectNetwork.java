package ru.projectkingdom.cityarchitect.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.projectkingdom.assistant.KingdomAssistantData;
import ru.projectkingdom.cityarchitect.KingdomCityArchitectMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CityArchitectNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KingdomCityArchitectMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final SessionRegistry SESSIONS = new SessionRegistry();
    private static DecisionConsumer decisionConsumer = decision -> { };
    private static boolean registered;

    private CityArchitectNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, CityArchitectPreviewPacket.class,
                CityArchitectPreviewPacket::encode, CityArchitectPreviewPacket::decode,
                CityArchitectPreviewPacket::handle);
        CHANNEL.registerMessage(1, CityArchitectDecisionPacket.class,
                CityArchitectDecisionPacket::encode, CityArchitectDecisionPacket::decode,
                CityArchitectDecisionPacket::handle);
    }

    public static void configure(DecisionConsumer consumer) {
        decisionConsumer = Objects.requireNonNull(consumer, "consumer");
    }

    public static Session open(ServerPlayer player, UUID assistant, UUID planId, UUID stageId,
                               long expiresAt, CityArchitectPreviewPacket preview) {
        Session session = SESSIONS.open(player.getUUID(), assistant,
                player.level().dimension().location().toString(), planId, stageId, expiresAt);
        CityArchitectPreviewPacket packet = new CityArchitectPreviewPacket(
                session.token(), planId, stageId, preview.title(), preview.explanation(),
                expiresAt, preview.markers());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        return session;
    }

    public static void sendDecision(CityArchitectDecisionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    static void handleDecision(ServerPlayer sender, CityArchitectDecisionPacket packet) {
        if (sender == null || sender.getServer() == null) {
            return;
        }
        UUID assistant = KingdomAssistantData.get(sender.getServer()).ownedBy(sender.getUUID())
                .map(KingdomAssistantData.OwnerState::entityId).orElse(null);
        DecisionResult result = SESSIONS.decide(sender.getUUID(), assistant,
                sender.level().dimension().location().toString(), sender.hasPermissions(4) ? 4 : 0,
                packet.token(), packet.confirm(), sender.getServer().overworld().getGameTime());
        if (result.status() == DecisionStatus.ACCEPTED) {
            decisionConsumer.accept(new AuthorizedDecision(sender, result.session(), packet.confirm()));
        }
    }

    public static final class SessionRegistry {
        private final Map<UUID, Session> sessions = new LinkedHashMap<>();

        public synchronized Session open(
                UUID owner, UUID assistant, String dimension, UUID planId, UUID stageId, long expiresAt
        ) {
            Session session = new Session(UUID.randomUUID(), owner, assistant, dimension,
                    planId, stageId, expiresAt);
            sessions.put(session.token(), session);
            return session;
        }

        public synchronized DecisionResult decide(
                UUID owner,
                UUID assistant,
                String dimension,
                int permissionLevel,
                UUID token,
                boolean confirm,
                long now
        ) {
            Session session = sessions.get(token);
            if (session == null) return new DecisionResult(DecisionStatus.BAD_TOKEN, null, confirm);
            if (now > session.expiresAt()) {
                sessions.remove(token);
                return new DecisionResult(DecisionStatus.EXPIRED, null, confirm);
            }
            if (permissionLevel < 4) return new DecisionResult(DecisionStatus.NO_PERMISSION, null, confirm);
            if (!session.owner().equals(owner)) return new DecisionResult(DecisionStatus.BAD_OWNER, null, confirm);
            if (!session.assistant().equals(assistant)) return new DecisionResult(DecisionStatus.BAD_ASSISTANT, null, confirm);
            if (!session.dimension().equals(dimension)) return new DecisionResult(DecisionStatus.BAD_DIMENSION, null, confirm);
            sessions.remove(token);
            return new DecisionResult(DecisionStatus.ACCEPTED, session, confirm);
        }
    }

    public enum DecisionStatus {
        ACCEPTED, BAD_TOKEN, EXPIRED, NO_PERMISSION, BAD_OWNER, BAD_ASSISTANT, BAD_DIMENSION
    }

    public record Session(
            UUID token,
            UUID owner,
            UUID assistant,
            String dimension,
            UUID planId,
            UUID stageId,
            long expiresAt
    ) {
        public Session {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(assistant, "assistant");
            dimension = Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(stageId, "stageId");
        }
    }

    public record DecisionResult(DecisionStatus status, Session session, boolean confirm) {
    }

    public record AuthorizedDecision(ServerPlayer player, Session session, boolean confirm) {
    }

    @FunctionalInterface
    public interface DecisionConsumer {
        void accept(AuthorizedDecision decision);
    }
}
