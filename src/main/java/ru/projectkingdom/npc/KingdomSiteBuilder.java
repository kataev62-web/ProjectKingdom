package ru.projectkingdom.npc;

import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Creates paid, server-validated survival sites from built-in templates or
 * immutable WorldEdit .schem snapshots. The original lectern is still supported.
 */
public final class KingdomSiteBuilder {
    private KingdomSiteBuilder() {}

    public static String place(ServerPlayer player, KingdomBuildingTypes.Type type) {
        if (type == null) return "Неизвестный тип здания.";
        return placeDesign(player, type.name(), KingdomBuildingTypes.design(type), false);
    }

    public static String placeSchematic(ServerPlayer player, String name) {
        if (!KingdomSchematicLibrary.validName(name)) return "Недопустимое имя схемы.";
        try {
            var design = KingdomSchematicLibrary.preview(player.getServer(), name);
            return placeDesign(player, name, design, true);
        } catch (IOException | IllegalArgumentException ex) {
            return "Ошибка .schem: " + ex.getMessage();
        }
    }

    private static String placeDesign(ServerPlayer player, String id,
                                      KingdomDesignEngine.Design design, boolean imported) {
        BlockPos marker = findLectern(player);
        if (marker == null) return "Установи чертёжный стол или кафедру на расстоянии до 4 блоков.";
        var db = KingdomConstructionData.get(player.getServer());
        String dimension = player.level().dimension().location().toString();
        if (db.atMarker(dimension, marker.getX(), marker.getY(), marker.getZ()) != null)
            return "На этом чертёжном столе уже зарегистрирована стройка.";

        int blueprintSlot = -1;
        if (!player.isCreative()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(
                        KingdomConstructionRegistry.BUILDING_BLUEPRINT.get())) {
                    blueprintSlot = i; break;
                }
            }
            if (blueprintSlot < 0) return "Для создания стройки нужен строительный чертёж.";
        }
        if (design == null || design.blocks().isEmpty()) return "Проект не содержит строительных блоков.";

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var cell : design.blocks()) {
            BlockPos target = marker.offset(2 + cell.x(), cell.y(), 2 + cell.z());
            minX = Math.min(minX, target.getX()); maxX = Math.max(maxX, target.getX());
            minY = Math.min(minY, target.getY()); maxY = Math.max(maxY, target.getY());
            minZ = Math.min(minZ, target.getZ()); maxZ = Math.max(maxZ, target.getZ());
        }
        try {
            if (db.overlaps(player.getServer(), dimension, minX, minY, minZ,
                    maxX, maxY, maxZ))
                return "Эта площадка пересекается с существующей постройкой.";
        } catch (IllegalStateException ex) {
            return "Схема прежнего проекта недоступна: сначала восстанови файлы стройки.";
        }
        for (var cell : design.blocks()) {
            BlockPos target = marker.offset(2 + cell.x(), cell.y(), 2 + cell.z());
            if (target.getY() < player.serverLevel().getMinBuildHeight()
                    || target.getY() >= player.serverLevel().getMaxBuildHeight())
                return "Здание выходит за высоту мира.";
            if (!player.serverLevel().hasChunkAt(target))
                return "Прогрузи всю площадку перед созданием проекта.";
            if (!KingdomConstructionAccess.canBuild(
                    player.getServer(), player.serverLevel(), target, player.getUUID()))
                return "Нет права строить: " + target.toShortString();
            var requested = KingdomBlockStateCodec.parse(cell.material());
            if (!KingdomBlockStateCodec.buildable(requested))
                return "Невозможно построить из этого предмета: " + cell.material();
            var present = player.serverLevel().getBlockState(target);
            if (!present.isAir() && !present.equals(requested))
                return "Очисти площадку: мешает блок " + target.toShortString();
        }

        String designKey = id;
        if (imported) {
            try {
                designKey = KingdomSchematicLibrary.freeze(player.getServer(), id);
            } catch (IOException ex) {
                return "Не удалось закрепить копию схемы: " + ex.getMessage();
            }
        }
        var project = db.add(player.getUUID(), dimension, designKey,
            marker.getX() + 2, marker.getY(), marker.getZ() + 2);
        if (blueprintSlot >= 0) {
            player.getInventory().getItem(blueprintSlot).shrink(1);
            player.getInventory().setChanged();
        }
        return "Проект «" + KingdomConstructionPlans.title(designKey)
            + "» создан. ПКМ по столу: управление. ID: " + project.id();
    }

    public static BlockPos findLectern(ServerPlayer player) {
        BlockPos base = player.blockPosition();
        BlockPos closest = null;
        double best = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (!KingdomConstructionMarkers.isMarker(player.serverLevel(), pos)) continue;
                    double distance = pos.distSqr(base);
                    if (distance < best) { best = distance; closest = pos; }
                }
        return closest;
    }

    /**
     * Optional player batch-assist, also consuming real stock from the same
     * physical site depot as the NPC builder. No personal-inventory duplication.
     */
    public static String build(ServerPlayer player, KingdomConstructionData.Project project) {
        if (project == null || !KingdomConstructionData.get(player.getServer())
                .mayAssist(project.id(), player.getUUID()))
            return "Нет прав на стройку: попроси владельца пригласить тебя в бригаду.";
        if (!project.dimension().equals(player.level().dimension().location().toString()))
            return "Стройка в другом измерении.";
        BlockPos marker = new BlockPos(project.x() - 2, project.y(), project.z() - 2);
        if (!KingdomConstructionMarkers.isMarker(player.serverLevel(), marker))
            return "Чертёжный стол или кафедра удалены.";
        if (player.distanceToSqr(marker.getX() + .5, marker.getY(), marker.getZ() + .5) > 144)
            return "Подойди к стройке ближе.";
        int placed = 0, advanced = 0;
        KingdomConstructionWork.Step last = null;
        for (int i = 0; i < 32; i++) {
            last = KingdomConstructionWork.placeNext(player.getServer(), player.serverLevel(), project);
            if (!last.advanced()) break;
            advanced++;
            if (last.result() == KingdomConstructionWork.Result.PLACED) placed++;
        }
        var design = KingdomConstructionPlans.resolve(player.getServer(), project.design());
        int total = design == null ? 0 : design.blocks().size();
        int progress = KingdomConstructionData.get(player.getServer()).progress(project.id());
        return "Строительство: " + progress + "/" + total + " (поставлено " + placed
            + ", учтено " + advanced + "). " + (last == null ? "" : last.result().message());
    }
}
