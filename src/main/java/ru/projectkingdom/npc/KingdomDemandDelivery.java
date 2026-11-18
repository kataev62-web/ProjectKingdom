package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The delivery service of one running server.
 *
 * <p>The service owns the durable state of the demand board and the markers of
 * the open deliveries. It lives inside the world, so a backup of the world
 * carries the board and the markers together with the containers they belong
 * to.
 *
 * <p>The game path binds the place of the four containers of a delivery. Until
 * a binding exists the service refuses every delivery: no goods move without
 * a known container, a known owner and a known state.
 */
public final class KingdomDemandDelivery {
    /** The directory of the delivery service inside the world. */
    public static final String DIRECTORY = "data/kingdom_demand";

    private static final Map<String, KingdomDemandDelivery> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final DurableDemandBoardCatalog board;
    private final Path pending;
    private final Path transactions;
    private volatile SettlementMinecraftEndpoints.SlotProvider provider;

    private KingdomDemandDelivery(MinecraftServer server) throws IOException {
        this.server = server;
        Path directory = directory(server);
        Files.createDirectories(directory);
        this.pending = directory.resolve("pending");
        this.transactions = directory.resolve("transactions");
        Files.createDirectories(pending);
        Files.createDirectories(transactions);
        this.board = new DurableDemandBoardCatalog(directory.resolve("board.snapshot"));
    }

    /** The service of the given server. */
    public static KingdomDemandDelivery get(MinecraftServer server) {
        String key = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().toString();
        return INSTANCES.computeIfAbsent(key, ignored -> {
            try {
                return new KingdomDemandDelivery(server);
            } catch (IOException ex) {
                throw new IllegalStateException("the delivery service of the world is not available", ex);
            }
        });
    }

    /** The directory of the service inside the world. */
    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY);
    }

    /** The durable board of the listings. */
    public DurableDemandBoardCatalog board() { return board; }

    /** The directory of the markers of the open deliveries. */
    public Path pending() { return pending; }

    /** The directory of the transactions of the older settlement gate. */
    public Path transactions() { return transactions; }

    /**
     * Binds the place of the four containers of a delivery.
     *
     * <p>The game path calls this when the seller opens the delivery. Without
     * a binding the service refuses every delivery.
     */
    public void bind(SettlementMinecraftEndpoints.SlotProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /** Whether a binding exists. */
    public boolean bound() { return provider != null; }

    /**
     * The adapter of the settlement authority.
     *
     * <p>Without a binding the resolver finds no container: the delivery is
     * refused instead of guessed.
     */
    public DemandSettlementDeliveryAdapter adapter(SettlementAuthority authority) {
        return new DemandSettlementDeliveryAdapter(authority, (listing, delivery) -> {
            SettlementMinecraftEndpoints.SlotProvider current = provider;
            if (current == null) return null;
            return new SettlementMinecraftEndpoints(current).resolve(listing, delivery);
        }, transactions);
    }

    /** The coordinator of the deliveries. */
    public DemandDeliveryCoordinator coordinator(SettlementAuthority authority) {
        return new DemandDeliveryCoordinator(board, pending, adapter(authority));
    }

    /** The markers of the open deliveries. */
    public List<String> openMarkers() {
        List<String> out = new java.util.ArrayList<>();
        try (var files = Files.newDirectoryStream(pending, "*.pending")) {
            for (Path path : files) {
                List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.US_ASCII);
                out.add(path.getFileName() + ": " + String.join(" / ", lines));
            }
        } catch (IOException ex) {
            return List.of("маркеры недоступны: " + ex.getMessage());
        }
        return out;
    }

    /**
     * Closes the marker of a delivery.
     *
     * <p>The operator has to check the four containers by hand before: the
     * marker is the only trace of a delivery that may have moved goods
     * partially.
     */
    public boolean closeMarker(String deliveryId) {
        for (String part : deliveryId.split("[^A-Za-z0-9-]")) {
            if (part.isEmpty()) continue;
            Path marker = pending.resolve(part + ".pending");
            try {
                if (Files.deleteIfExists(marker)) return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return false;
    }

    /** Drops the cached service of a stopped server. */
    public static void forget(MinecraftServer server) {
        String key = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().toString();
        INSTANCES.remove(key);
    }
}
