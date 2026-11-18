package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative, shared survival construction step for players and NPCs.
 * One step = one existing plan cell; real storage provides one actual ItemStack.
 * Never force-load chunks or create materials, currency, or unregistered blocks.
 */
public final class KingdomConstructionWork {
    private KingdomConstructionWork() {}

    public enum Result {
        PLACED("Блок поставлен"),
        ALREADY_PRESENT("Блок уже построен; прогресс учтён"),
        COMPLETED("Здание завершено"),
        NO_PROJECT("Чертёж не найден"),
        NO_MARKER("Нет чертёжного стола или строительной кафедры"),
        NO_DEPOT("Поставь сундук или бочку рядом с чертёжным столом"),
        UNLOADED("Чанк стройки не загружен"),
        NO_RIGHTS("Нет права строительства на этом участке"),
        OBSTACLE("Мешает существующий блок"),
        NO_MATERIALS("На складе нет подходящего материала без NBT"),
        STORAGE_REJECTED("Склад отклонил списание; блок возвращён, материалы не списаны"),
        REQUIRES_REVIEW("Стройка остановлена: неоднозначная операция, требуется сверка блоков и склада"),
        INVALID_BLOCK("Материал из чертежа недоступен"),
        CANCELLED("Стройка отменена");

        private final String message;
        Result(String message) { this.message = message; }
        public String message() { return message; }
    }

    public record Step(Result result, BlockPos target, String material) {
        public boolean advanced() {
            return result == Result.PLACED || result == Result.ALREADY_PRESENT;
        }
    }

    public static Step next(MinecraftServer server, KingdomConstructionData.Project project) {
        if (project == null) return new Step(Result.NO_PROJECT, null, "");
        var design = KingdomConstructionPlans.resolve(server, project.design());
        if (design == null) return new Step(Result.INVALID_BLOCK, null, "");
        var blocks = design.blocks();
        int index = KingdomConstructionData.get(server).progress(project.id());
        if (index >= blocks.size()) return new Step(Result.COMPLETED, null, "");
        var cell = blocks.get(index);
        return new Step(Result.PLACED,
            new BlockPos(project.x() + cell.x(), project.y() + cell.y(), project.z() + cell.z()),
            cell.material());
    }

    public static Step placeNext(MinecraftServer server, ServerLevel level,
                                 KingdomConstructionData.Project project) {
        Step next = next(server, project);
        if (project == null) return next;
        var sites = KingdomConstructionData.get(server);
        // A persisted in-flight attempt might have placed a block without a
        // matching inventory debit before a crash. Never replay automatically.
        if (sites.hasPendingStep(project.id()))
            return new Step(Result.REQUIRES_REVIEW, next.target(), next.material());
        if (next.result() != Result.PLACED) return next;
        if (project.status() == KingdomConstructionData.Status.CANCELLED)
            return new Step(Result.CANCELLED, next.target(), next.material());
        if (!project.dimension().equals(level.dimension().location().toString()))
            return new Step(Result.UNLOADED, next.target(), next.material());

        BlockPos marker = sites.marker(project);
        if (!level.hasChunkAt(marker) || !KingdomConstructionMarkers.isMarker(level, marker))
            return new Step(Result.NO_MARKER, next.target(), next.material());
        BlockPos target = next.target();
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()
                || !level.hasChunkAt(target))
            return new Step(Result.UNLOADED, target, next.material());
        if (!KingdomConstructionAccess.canBuild(server, level, target, project.owner()))
            return new Step(Result.NO_RIGHTS, target, next.material());

        BlockState desired;
        try {
            desired = KingdomBlockStateCodec.parse(next.material());
        } catch (IllegalArgumentException error) {
            return new Step(Result.INVALID_BLOCK, target, next.material());
        }
        if (!KingdomBlockStateCodec.buildable(desired))
            return new Step(Result.INVALID_BLOCK, target, next.material());
        if (level.getBlockState(target).equals(desired)) {
            advance(server, project);
            return new Step(Result.ALREADY_PRESENT, target, next.material());
        }

        BlockState occupying = level.getBlockState(target);
        // A different orientation of the SAME non-BE block reuses its real
        // material. No item is charged, and a failed update never advances.
        if (occupying.is(desired.getBlock())) {
            if (occupying.hasBlockEntity() || !desired.canSurvive(level, target)
                    || !level.setBlock(target, desired, 3)
                    || !level.getBlockState(target).equals(desired))
                return new Step(Result.OBSTACLE, target, next.material());
            advance(server, project);
            return new Step(Result.ALREADY_PRESENT, target, next.material());
        }
        // Never demolish a pre-existing block, even "harmless" vegetation:
        // world drops + inventory + progress cannot be rolled back atomically.
        if (!occupying.isAir())
            return new Step(Result.OBSTACLE, target, next.material());
        if (!desired.canSurvive(level, target))
            return new Step(Result.OBSTACLE, target, next.material());

        Container depot = depot(server, level, marker, project.owner());
        if (depot == null) return new Step(Result.NO_DEPOT, target, next.material());
        KingdomConstructionMaterials.Prepared material = null;
        for (int slot = 0; slot < depot.getContainerSize(); slot++) {
            material = KingdomConstructionMaterials.prepare(depot, slot, desired.getBlock().asItem());
            if (material != null) break;
        }
        if (material == null) return new Step(Result.NO_MATERIALS, target, next.material());

        // Record an in-flight intent before either resource changes. This
        // survives normal SavedData save/load, but is NOT an fsync crash journal.
        if (!sites.beginPendingStep(project.id(), target, next.material()))
            return new Step(Result.REQUIRES_REVIEW, target, next.material());

        // Place first: on a rejected placement the physical material is still
        // in storage. An actual, verified setItem debit follows a verified block.
        boolean placed;
        try {
            placed = level.setBlock(target, desired, 3);
        } catch (RuntimeException failure) {
            return new Step(Result.REQUIRES_REVIEW, target, next.material());
        }
        if (!placed || !level.getBlockState(target).equals(desired)) {
            if (rollbackFreshBlock(level, target, desired)) {
                sites.clearPendingStep(project.id());
                return new Step(Result.OBSTACLE, target, next.material());
            }
            return new Step(Result.REQUIRES_REVIEW, target, next.material());
        }

        var debit = KingdomConstructionMaterials.debit(depot, material);
        if (debit == KingdomConstructionMaterials.Debit.PAID) {
            sites.finishPendingStep(project.id(), KingdomConstructionPlans.resolve(server, project.design()).blocks().size());
            return new Step(Result.PLACED, target, next.material());
        }
        if (debit == KingdomConstructionMaterials.Debit.REJECTED
                && rollbackFreshBlock(level, target, desired)) {
            sites.clearPendingStep(project.id());
            return new Step(Result.STORAGE_REJECTED, target, next.material());
        }
        // Partial inventory writes or failed world rollback require manual
        // investigation, never an automatic retry or invented refund item.
        return new Step(Result.REQUIRES_REVIEW, target, next.material());
    }

    /** Roll back ONLY the fresh intended block, never arbitrary modded changes. */
    private static boolean rollbackFreshBlock(ServerLevel level, BlockPos target, BlockState desired) {
        BlockState actual = level.getBlockState(target);
        if (actual.isAir()) return true;
        if (!actual.equals(desired) || actual.hasBlockEntity()) return false;
        return level.setBlock(target, Blocks.AIR.defaultBlockState(), 3)
            && level.getBlockState(target).isAir();
    }

    /** Existing obstacles are never destroyed by a schematic builder. */
    public static boolean canClearObstacle(ServerLevel level, BlockPos pos, BlockState state) {
        return state != null && state.isAir() && state.getFluidState().isEmpty();
    }

    private static void advance(MinecraftServer server, KingdomConstructionData.Project project) {
        var design = KingdomConstructionPlans.resolve(server, project.design());
        if (design == null) return;
        KingdomConstructionData.get(server).advance(project.id(), 1, design.blocks().size());
    }

    static Container depot(MinecraftServer server, ServerLevel level,
                                   BlockPos marker, UUID owner) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = marker.relative(direction);
            if (!level.hasChunkAt(adjacent)
                    || !KingdomConstructionAccess.canBuild(server, level, adjacent, owner)) continue;
            BlockState state = level.getBlockState(adjacent);
            // A double chest is ONE physical inventory: both halves must be
            // loaded and owned, or the combined depot is not authorized.
            if (state.getBlock() instanceof ChestBlock
                    && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                boolean pairedAndOwned = false;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos other = adjacent.relative(side);
                    if (KingdomContainerAccess.samePhysicalStorage(level, adjacent, other)) {
                        pairedAndOwned = KingdomConstructionAccess.canBuild(
                            server, level, other, owner);
                        break;
                    }
                }
                if (!pairedAndOwned) continue;
            }
            Container result = KingdomContainerAccess.resolve(level, adjacent);
            if (result != null) return result;
        }
        return null;
    }
}
