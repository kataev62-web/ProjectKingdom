package ru.projectkingdom.assistant.checks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.administration.KingdomCentralWarehouseRooms;
import ru.projectkingdom.assistant.AssistantDiagnosticCheck;
import ru.projectkingdom.assistant.AssistantDiagnosticContext;
import ru.projectkingdom.assistant.AssistantFinding;
import ru.projectkingdom.npc.KingdomCivicsData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Registered room bounds and their live, already-loaded storage bindings. */
public final class WarehouseDiagnosticCheck implements AssistantDiagnosticCheck {
    @Override public String id() { return "warehouse"; }
    @Override public String titleRu() { return "Центральные склады"; }

    @Override public List<AssistantFinding> run(AssistantDiagnosticContext context) {
        if (context.server() == null) return List.of();
        var towns = new ArrayList<>(KingdomCivicsData.get(context.server()).towns());
        towns.sort(Comparator.comparing(town -> town.id().toString()));
        var rooms = KingdomCentralWarehouseRooms.get(context.server());
        var administration = KingdomAdministrationData.get(context.server());
        List<AssistantFinding> findings = new ArrayList<>();
        for (var town : towns) for (var room : rooms.rooms(town.id())) {
            if (findings.size() >= 64) return List.copyOf(findings);
            ServerLevel level = level(context, room.dimension());
            if (level == null || !loaded(level, room.min()) || !loaded(level, room.max())) {
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "Комната склада не прогружена",
                        "Нельзя безопасно проверить помещение " + room.id() + " (" + room.dimensions() + ").",
                        "Подойдите к центральному складу и прогрузите оба крайних чанка.",
                        "Повторная диагностика должна увидеть оба угла комнаты."));
                continue;
            }
            var bindings = administration.containerBindings(
                    town.id(), AdministrativeBindingRole.WAREHOUSE_STORAGE).stream()
                    .filter(binding -> room.dimension().equals(binding.dimension())
                            && room.contains(BlockPos.of(binding.packedPos()))).toList();
            if (bindings.isEmpty()) {
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "В комнате нет привязанного хранилища",
                        "Помещение " + room.id() + " зарегистрировано, но не содержит складской привязки.",
                        "Установите сундук, бочку или поддерживаемое TFC-хранилище внутри комнаты.",
                        "В меню склада должна появиться активная привязка помещения."));
                continue;
            }
            for (var binding : bindings) {
                BlockPos position = BlockPos.of(binding.packedPos());
                if (!loaded(level, position)) {
                    findings.add(finding(AssistantFinding.Severity.WARNING,
                            "Хранилище не прогружено", "Привязка " + position.toShortString()
                                    + " сейчас недоступна без загрузки чанка.",
                            "Прогрузите чанк хранилища.",
                            "Диагностика должна прочитать живой контейнер."));
                } else if (!(level.getBlockEntity(position) instanceof Container)) {
                    findings.add(finding(AssistantFinding.Severity.CRITICAL,
                            "Привязка склада повреждена", "По координатам " + position.toShortString()
                                    + " больше нет поддерживаемого контейнера.",
                            "Удалите устаревшую привязку и зарегистрируйте реальное хранилище.",
                            "Координата должна содержать доступный Container."));
                }
            }
        }
        return List.copyOf(findings);
    }

    private static ServerLevel level(AssistantDiagnosticContext context, String dimension) {
        for (ServerLevel level : context.server().getAllLevels())
            if (dimension.equals(level.dimension().location().toString())) return level;
        return null;
    }

    private static boolean loaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private static AssistantFinding finding(AssistantFinding.Severity severity, String title,
                                            String reason, String recommendation,
                                            String verification) {
        return new AssistantFinding(AssistantFinding.Category.WAREHOUSE, severity,
                title, reason, recommendation, verification);
    }
}
