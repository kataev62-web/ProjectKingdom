package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import ru.projectkingdom.network.ArchitectPreviewPacket;
import ru.projectkingdom.network.ArchitectDecisionPacket;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.assistant.AssistantPreviewService;
import ru.projectkingdom.assistant.KingdomAssistantData;

/**
 * Ephemeral, server-owned architect selection. Clients may request only
 * positioning and rotation; they never choose the material list or blueprint
 * bytes in a confirmation packet.
 */
public final class KingdomArchitectSessions {
    private KingdomArchitectSessions() {}
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final long LIFE_TICKS = 20L * 60L * 10L;
    public enum Purpose { SURVIVAL_SITE, ASSISTANT_CREATIVE }
    public record Session(UUID token, Purpose purpose, UUID owner, UUID assistant,
                          String design, BlockPos marker, String dimension, long expires) {}

    public static void cancel(UUID player) { ACTIVE.remove(player); }

    public static void beginBuiltin(ServerPlayer player, KingdomBuildingTypes.Type type) {
        beginBuiltin(player,type,null);
    }
    public static void beginBuiltin(ServerPlayer player, KingdomBuildingTypes.Type type, BlockPos office) {
        if (type != null) begin(player,type.name(),office);
    }

    public static void beginSchematic(ServerPlayer player,String name) {
        beginSchematic(player,name,null);
    }
    public static void beginSchematic(ServerPlayer player,String name, BlockPos office) {
        if (!KingdomSchematicLibrary.validName(name)) {
            tell(player,"Неверное название схемы.");return;
        }
        try {
            // Snapshotting here freezes the file for preview AND later build.
            begin(player,KingdomSchematicLibrary.freeze(player.getServer(),name),office);
        } catch (IOException | IllegalArgumentException ex) {
            tell(player,"Нельзя открыть схему: "+ex.getMessage());
        }
    }

    /** Opens the existing client preview without creating a survival construction site. */
    public static boolean beginAssistant(
            ServerPlayer owner, UUID assistantId, String designKey, BlockPos anchor) {
        if (owner == null || owner.getServer() == null || assistantId == null
                || designKey == null || anchor == null || !owner.hasPermissions(4)) return false;
        var state = KingdomAssistantData.get(owner.getServer()).ownedBy(owner.getUUID());
        String dimension = owner.serverLevel().dimension().location().toString();
        if (state.isEmpty() || !assistantId.equals(state.get().entityId())
                || !dimension.equals(state.get().dimension())) return false;
        if (!owner.serverLevel().hasChunkAt(anchor)
                || anchor.getY() < owner.serverLevel().getMinBuildHeight()
                || anchor.getY() >= owner.serverLevel().getMaxBuildHeight()) return false;
        return open(owner, designKey, anchor, Purpose.ASSISTANT_CREATIVE, assistantId);
    }

    private static void begin(ServerPlayer player,String design,BlockPos office) {
        if (player.getServer()==null)return;
        // An enterprise menu remembers the actual core it was opened from.
        // Free-standing drafting tables keep the old nearby-search behavior.
        BlockPos marker=office!=null ? office : KingdomSiteBuilder.findLectern(player);
        if (marker==null || !KingdomConstructionMarkers.isMarker(player.serverLevel(),marker)){
            tell(player,office!=null
                ?"Блок строительной конторы больше не доступен."
                :"Подойди к строительной конторе или чертёжному столу.");return;
        }
        if (!KingdomArchitectSiteAccess.mayUse(player,marker)) {
            tell(player,"Нет прав пользоваться этой строительной конторой.");return;
        }
        var storage=KingdomConstructionData.get(player.getServer());
        if(storage.atMarker(player.level().dimension().location().toString(),
                marker.getX(),marker.getY(),marker.getZ())!=null){
            tell(player,"На этом столе уже зарегистрирована стройка.");return;
        }
        open(player, design, marker, Purpose.SURVIVAL_SITE, null);
    }

    private static boolean open(ServerPlayer player, String design, BlockPos marker,
                                Purpose purpose, UUID assistantId) {
        var source=KingdomConstructionPlans.resolve(player.getServer(),design);
        if(source==null || source.blocks().isEmpty()){
            tell(player,"Не удалось прочитать строительную схему: "+design);return false;
        }
        KingdomDesignEngine.Design shape;
        try {
            shape=KingdomArchitectDesignRotation.rotate(source,0);
        } catch (IllegalArgumentException error) {
            tell(player,"Проект «"+KingdomConstructionPlans.title(design)
                +"» не может быть показан: "+error.getMessage());
            return false;
        }
        // Bounded preview packets: mostly exterior blocks for an intelligible
        // textured 3D model; full design remains exclusively on the server.
        var preview=ArchitectPreviewPacket.fromDesign(shape,
                UUID.randomUUID(),marker,KingdomConstructionPlans.title(design));
        if(preview==null){tell(player,"Схема не помещается в безопасный предпросмотр.");return false;}
        var session=new Session(preview.token(),purpose,player.getUUID(),assistantId,
            design,marker.immutable(),
            player.level().dimension().location().toString(),
            player.serverLevel().getGameTime()+LIFE_TICKS);
        ACTIVE.put(player.getUUID(),session);
        KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),preview);
        tell(player,"Схема «"+KingdomConstructionPlans.title(design)
            +"» выбрана. Отправляю 3D-макет. Если окно не появилось — проверь клиентский JAR.");
        return true;
    }

    public static void decide(ServerPlayer player, ArchitectDecisionPacket packet) {
        Session session=ACTIVE.get(player.getUUID());
        if(session==null || !session.token().equals(packet.token())){
            reply(player,false,"Предпросмотр устарел. Выбери здание повторно.");return;
        }
        if(packet.cancel()){
            ACTIVE.remove(player.getUUID());
            if (session.purpose() == Purpose.ASSISTANT_CREATIVE && player.getServer() != null)
                KingdomAssistantData.get(player.getServer()).finishTask(
                        player.getUUID(), "Творческий предпросмотр отменён");
            reply(player,true,"Предпросмотр отменён. Блоки не изменены.");
            return;
        }
        if (session.purpose() == Purpose.ASSISTANT_CREATIVE) {
            decideAssistant(player, packet, session);
            return;
        }
        if(player.getServer()==null || session.expires()<player.serverLevel().getGameTime()
                || !session.dimension().equals(player.level().dimension().location().toString())){
            ACTIVE.remove(player.getUUID());
            reply(player,false,"Сеанс архитектора истёк или изменилось измерение.");return;
        }
        var origin=packet.origin();
        var marker=session.marker();
        if (Math.abs((long)origin.getX()-marker.getX())>80 ||
                Math.abs((long)origin.getZ()-marker.getZ())>80 ||
                Math.abs((long)origin.getY()-marker.getY())>32 ||
                packet.rotation()<0 || packet.rotation()>3) {
            reply(player,false,"Проект слишком далеко от строительного двора.");return;
        }
        if (!player.serverLevel().hasChunkAt(marker)
                || !KingdomConstructionMarkers.isMarker(player.serverLevel(),marker)) {
            reply(player,false,"Чертёжный стол недоступен.");return;
        }
        String oriented=KingdomConstructionPlans.oriented(session.design(),packet.rotation());
        String result=KingdomSiteBuilder.placeAt(player,oriented,marker,origin);
        boolean accepted=result.startsWith("Проект «");
        if(accepted) ACTIVE.remove(player.getUUID());
        reply(player,accepted,result);
    }

    private static void decideAssistant(
            ServerPlayer player, ArchitectDecisionPacket packet, Session session) {
        if (player.getServer() == null) {
            reply(player, false, "Сервер помощника недоступен.");
            return;
        }
        UUID savedAssistant = KingdomAssistantData.get(player.getServer())
                .ownedBy(player.getUUID()).map(KingdomAssistantData.OwnerState::entityId)
                .orElse(null);
        var proof = new AssistantPreviewService.SessionProof(
                session.token(), session.owner(), session.assistant(), session.dimension(),
                session.marker(), session.expires());
        var attempt = new AssistantPreviewService.DecisionAttempt(
                packet.token(), player.getUUID(), savedAssistant,
                player.hasPermissions(4) ? 4 : 0,
                player.serverLevel().dimension().location().toString(),
                packet.origin(), packet.rotation(), player.serverLevel().getGameTime());
        String oriented;
        try {
            oriented = KingdomConstructionPlans.oriented(session.design(), packet.rotation());
        } catch (IllegalArgumentException forged) {
            reply(player, false, "Недопустимый поворот или проект.");
            return;
        }
        AssistantPreviewService.QueueResult[] queued = {null};
        var authorization = AssistantPreviewService.authorizeAndQueue(proof, attempt,
                () -> queued[0] = AssistantPreviewService.queue(
                        player, session.assistant(), oriented, packet.origin()));
        if (authorization != AssistantPreviewService.Authorization.ACCEPTED) {
            reply(player, false, authorizationMessage(authorization));
            return;
        }
        if (queued[0] != AssistantPreviewService.QueueResult.QUEUED) {
            reply(player, false, "Предложение отклонено серверной проверкой: " + queued[0] + ".");
            return;
        }
        ACTIVE.remove(player.getUUID());
        KingdomAssistantData.get(player.getServer()).finishTask(
                player.getUUID(), "Творческий проект подтверждён: "
                        + KingdomConstructionPlans.title(oriented));
        reply(player, true, "Творческий проект подтверждён и поставлен в очередь помощника.");
    }

    private static String authorizationMessage(AssistantPreviewService.Authorization result) {
        return switch (result) {
            case ACCEPTED -> "Проект подтверждён.";
            case INVALID_PACKET -> "Повреждённый пакет предпросмотра.";
            case STALE_TOKEN -> "Предпросмотр устарел. Выбери проект повторно.";
            case WRONG_OWNER -> "Подтвердить проект может только его владелец.";
            case WRONG_ASSISTANT -> "Привязка помощника изменилась.";
            case NOT_ADMIN -> "Для творческого проекта нужен уровень прав 4.";
            case EXPIRED -> "Сеанс предпросмотра истёк.";
            case DIMENSION_CHANGED -> "После открытия изменилось измерение.";
            case OUT_OF_RANGE -> "Проект расположен дальше допустимых 80/32/80 блоков.";
            case INVALID_ROTATION -> "Недопустимый поворот проекта.";
        };
    }

    private static void reply(ServerPlayer player,boolean accepted,String message) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "[Архитектор] "+message));
        KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),
            new ru.projectkingdom.network.ArchitectResultPacket(accepted,message));
    }
    private static void tell(ServerPlayer player,String message) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "[Архитектор] "+message));
    }
}
