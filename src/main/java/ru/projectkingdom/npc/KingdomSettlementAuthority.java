package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The settlement authority of one running server.
 *
 * <p>The durable log lives inside the world, next to the trade journal of the
 * enterprises, so a backup of the world always carries the decisions of the
 * deliveries together with the physical state they belong to.
 *
 * <p>The recovery runs when the server starts: a delivery that was decided
 * before the crash is finished, a delivery that was not decided is quarantined
 * for an operator. Nothing is ever applied automatically without a decision.
 */
public final class KingdomSettlementAuthority {
    /** Directory of the durable log inside the world. */
    public static final String DIRECTORY = "data/kingdom_settlement";

    private static final Map<String, KingdomSettlementAuthority> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final SettlementAuthority authority;
    private volatile String lastRecovery = "восстановление ещё не выполнялось";

    private KingdomSettlementAuthority(MinecraftServer server) throws IOException {
        this.server = server;
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY);
        Files.createDirectories(directory);
        this.authority = new SettlementAuthority(directory);
    }

    /** The authority of the given server. */
    public static KingdomSettlementAuthority get(MinecraftServer server) {
        String key = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().toString();
        return INSTANCES.computeIfAbsent(key, ignored -> {
            try {
                return new KingdomSettlementAuthority(server);
            } catch (IOException ex) {
                throw new IllegalStateException("the settlement log of the world is not available", ex);
            }
        });
    }

    /** The directory of the durable log inside the world. */
    public Path directory() {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY);
    }

    /** The authority itself. */
    public SettlementAuthority authority() { return authority; }

    /**
     * Finishes the deliveries that were decided before the server stopped and
     * quarantines the ones that were not.
     *
     * <p>A container that cannot be rebuilt (a chunk that is not loaded, a
     * right that was revoked) is not touched: the delivery is quarantined.
     */
    public SettlementAuthority.RecoveryReport recover() throws IOException {
        Map<String, SettlementAuthority.Container> registry = new LinkedHashMap<>();
        for (String id : authority.pendingContainers()) {
            SettlementMinecraftPorts.Port port = SettlementMinecraftPorts.resolve(server, id);
            if (port != null) registry.put(id, port);
        }
        SettlementAuthority.RecoveryReport report = authority.recover(registry);
        StringBuilder summary = new StringBuilder();
        summary.append("решений: ").append(report.transactions().size())
               .append(", карантин: ").append(report.quarantined().size());
        if (report.journalDamaged()) summary.append(", журнал повреждён");
        lastRecovery = summary.toString();
        return report;
    }

    /** A short human readable status for the operators. */
    public String status() {
        StringBuilder out = new StringBuilder();
        out.append("Журнал расчётов: ").append(directory());
        try {
            out.append(" (").append(authority.journalSize()).append(" байт)");
        } catch (IOException ex) {
            out.append(" (недоступен)");
        }
        out.append("\nВосстановление: ").append(lastRecovery);
        var transactions = authority.transactions();
        out.append("\nДоставок в журнале: ").append(transactions.size());
        long quarantined = transactions.stream()
            .filter(view -> view.state() == SettlementAuthority.State.QUARANTINED).count();
        out.append(", в карантине: ").append(quarantined);
        return out.toString();
    }

    /** The quarantined deliveries with their reason. */
    public String quarantineReport() {
        StringBuilder out = new StringBuilder();
        var transactions = authority.transactions();
        if (transactions.isEmpty()) return "Журнал расчётов пуст.";
        for (var view : transactions) {
            if (view.state() != SettlementAuthority.State.QUARANTINED) continue;
            out.append(view.id()).append(" — ")
               .append(view.item()).append(" x").append(view.quantity())
               .append(", причина: ").append(view.problem()).append('\n');
        }
        if (out.isEmpty()) out.append("Карантин пуст.");
        return out.toString();
    }

    /** Drops the cached authority of a stopped server. */
    public static void forget(MinecraftServer server) {
        String key = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().toString();
        INSTANCES.remove(key);
    }
}
