package ru.projectkingdom.npc;

import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Server side commands and the start up hook of the settlement authority.
 *
 * <p>Reading the state of the journal needs the operator level, changing it
 * needs the administrator level. The commands never move items and never
 * decide a delivery: they only show the state, close a quarantine or compact
 * the log.
 */
@Mod.EventBusSubscriber(modid = "kingdomnpc", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomSettlementCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private KingdomSettlementCommands() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    /** The recovery runs once the world is loaded. */
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        try {
            var report = KingdomSettlementAuthority.get(event.getServer()).recover();
            LOGGER.info("[kingdom] settlement recovery: deliveries={} quarantined={} journalDamaged={}",
                    report.transactions().size(), report.quarantined().size(), report.journalDamaged());
        } catch (Exception ex) {
            LOGGER.error("[kingdom] settlement recovery failed", ex);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        KingdomSettlementAuthority.forget(event.getServer());
        KingdomDemandDelivery.forget(event.getServer());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("settlement")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> {
                reply(context.getSource(), KingdomSettlementAuthority
                        .get(context.getSource().getServer()).status());
                return 1;
            }))
            .then(Commands.literal("quarantine").executes(context -> {
                reply(context.getSource(), KingdomSettlementAuthority
                        .get(context.getSource().getServer()).quarantineReport());
                return 1;
            }))
            .then(Commands.literal("recover")
                .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    try {
                        var report = KingdomSettlementAuthority
                                .get(context.getSource().getServer()).recover();
                        reply(context.getSource(), "Восстановление выполнено. Доставок: "
                                + report.transactions().size() + ", карантин: "
                                + report.quarantined().size()
                                + (report.journalDamaged() ? ", журнал повреждён." : "."));
                    } catch (Exception ex) {
                        reply(context.getSource(), "Восстановление не выполнено: " + ex.getMessage());
                        return 0;
                    }
                    return 1;
                }))
            .then(Commands.literal("compact")
                .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    try {
                        int kept = KingdomSettlementAuthority
                                .get(context.getSource().getServer()).authority().compact();
                        reply(context.getSource(), "Журнал расчётов ужат, записей осталось: " + kept);
                    } catch (Exception ex) {
                        reply(context.getSource(), "Журнал не ужат: " + ex.getMessage());
                        return 0;
                    }
                    return 1;
                }))
            .then(Commands.literal("pending").executes(context -> {
                var markers = KingdomDemandDelivery.get(context.getSource().getServer()).openMarkers();
                if (markers.isEmpty()) reply(context.getSource(), "Открытых доставок нет.");
                for (String marker : markers)
                    reply(context.getSource(), marker + ". Проверьте четыре сундука вручную перед закрытием.");
                return markers.isEmpty() ? 0 : 1;
            }).requires(source -> source.hasPermission(2)))
            .then(Commands.literal("pending-resolve")
            .requires(source -> source.hasPermission(4))
            .then(Commands.argument("uuid", StringArgumentType.word()).executes(context ->
                    closeMarker(context.getSource(), StringArgumentType.getString(context, "uuid")))))
            .then(Commands.literal("resolve")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("uuid", StringArgumentType.word())
                    .then(Commands.literal("applied").executes(context ->
                            resolve(context.getSource(), StringArgumentType.getString(context, "uuid"), true)))
                    .then(Commands.literal("not_applied").executes(context ->
                            resolve(context.getSource(), StringArgumentType.getString(context, "uuid"), false)))));
    }

    private static int resolve(CommandSourceStack source, String raw, boolean applied) {
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            reply(source, "Некорректный идентификатор доставки: " + raw);
            return 0;
        }
        try {
            var authority = KingdomSettlementAuthority.get(source.getServer()).authority();
            var resolution = applied
                    ? SettlementAuthority.Resolution.CONFIRMED_APPLIED
                    : SettlementAuthority.Resolution.CONFIRMED_NOT_APPLIED;
            authority.resolve(id, resolution);
            reply(source, applied
                    ? "Доставка " + id + " подтверждена как выполненная."
                    : "Доставка " + id + " подтверждена как невыполненная.");
        } catch (Exception ex) {
            reply(source, "Решение не принято: " + ex.getMessage());
            return 0;
        }
        return 1;
    }

    /**
     * Closes the marker of an open delivery.
     *
     * <p>The marker is the trace of a delivery that may have moved goods
     * partially. Only an operator that checked the four containers by hand may
     * close it.
     */
    private static int closeMarker(CommandSourceStack source, String raw) {
        try {
            UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            reply(source, "Некорректный идентификатор доставки: " + raw);
            return 0;
        }
        boolean closed;
        try {
            closed = KingdomDemandDelivery.get(source.getServer()).closeMarker(raw);
        } catch (RuntimeException ex) {
            reply(source, "Маркеры доставки недоступны: " + ex.getMessage());
            return 0;
        }
        reply(source, closed
                ? "Маркер доставки " + raw + " закрыт. Предметы и монеты не изменялись."
                : "Маркер доставки " + raw + " не найден.");
        return closed ? 1 : 0;
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSystemMessage(Component.literal(message));
    }
}
