package ru.projectkingdom.assistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/** Server-only authorization boundary for assistant-addressed chat. */
public final class KingdomAssistantChat {
    public enum Authorization { AUTHORIZED, NOT_ADDRESSED, NOT_OWNER, NOT_ADMIN }

    private static final String HELP = "[Архитектор Авалона] Команды: статус, следуй за мной, "
            + "стой здесь, осмотри территорию, аудит поселения, предпросмотр <схема>, "
            + "строй, отмени, откати, диагностика, аудит модов.";

    private KingdomAssistantChat() {}

    public static Authorization authorize(
            KingdomAssistantData data, UUID actor, int permissionLevel, String raw) {
        Objects.requireNonNull(data, "data");
        if (!AssistantCommandParser.isAddressed(raw)) return Authorization.NOT_ADDRESSED;
        if (permissionLevel < 4) return Authorization.NOT_ADMIN;
        if (actor == null || data.ownedBy(actor).isEmpty()) return Authorization.NOT_OWNER;
        return Authorization.AUTHORIZED;
    }

    /** Returns true only when owner-addressed chat was consumed. */
    public static boolean handle(ServerPlayer player, String raw) {
        if (player == null || player.getServer() == null) return false;
        KingdomAssistantData data = KingdomAssistantData.get(player.getServer());
        Authorization authorization = authorize(data, player.getUUID(),
                player.hasPermissions(4) ? 4 : 0, raw);
        if (authorization != Authorization.AUTHORIZED) return false;
        if (raw.length() > AssistantCommandParser.MAX_RAW_LENGTH) {
            player.sendSystemMessage(Component.literal(
                    "[Архитектор Авалона] Команда слишком длинная: максимум 512 символов."));
            return true;
        }
        var parsed = AssistantCommandParser.parse(raw);
        if (parsed.isEmpty()) {
            player.sendSystemMessage(Component.literal(HELP));
            return true;
        }
        KingdomAssistantTasks.submit(player, parsed.get());
        return true;
    }
}
