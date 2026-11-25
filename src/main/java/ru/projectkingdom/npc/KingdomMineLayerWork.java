package ru.projectkingdom.npc;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The excavation of the mine, layer by layer.
 *
 * <p>The miner clears the whole horizontal layer of the surveyed footprint
 * before it touches the next one. Every block is dug with the real tool of
 * the resident: the ground with the real shovel, the rock and the ore with the
 * real pickaxe. The drops are the real drops of the block and they go into the
 * real bag of the resident.
 *
 * <p>The descent is a cell of the southern edge with a ladder: the ladder
 * comes from the depot of the enterprise and the deck of planks comes from the
 * same place. Nothing is created and nothing is mined where the rules of TFC
 * forbid it.
 *
 * <p>The progress of the excavation is the world itself: a block that is gone
 * stays gone after a rest, an unloaded chunk and a restart of the server.
 */
public final class KingdomMineLayerWork {
    private KingdomMineLayerWork(){}
    private static final double REACH_SQR = KingdomActionEngine.REACH_SQR;

    /** The name of a block for the diagnosis of the operator. */
    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * Digs the mine of the enterprise, one block per work tick.
     *
     * @return true when a block was really dug.
     */
    public static boolean tick(ServerLevel level, Villager npc, KingdomNpcData.Worker worker,
                               KingdomMineSettings.Shaft area, int levels,
                               KingdomMineSettings.Heading heading) {
        if (level == null || npc == null || worker == null || area == null) return false;
        int maxDepth = levels * KingdomMineSettings.LEVEL_INTERVAL;
        for (int layer = 0; layer <= maxDepth; layer++) {
            List<KingdomMineGalleryPlan.Cell> cells = new java.util.ArrayList<>(
                    KingdomMineCentralShaftPlan.layer(area, layer));
            BlockPos ladderCell = KingdomMineCentralShaftPlan.descent(area, layer);
            // Open and equip the escape route before the rest of this layer.
            // Keep the remaining MiniMax serpentine coordinates/order intact.
            cells.sort(java.util.Comparator.comparingInt(cell ->
                    cell.target().equals(ladderCell) ? 0 : 1));
            // Audit every coordinate, including pre-existing air/caves, before
            // work or a transition. A loaded first row never proves a loaded layer.
            for (var cell : cells) {
                BlockPos at = cell.target();
                if (!level.hasChunkAt(at) || !level.hasChunkAt(at.above())
                        || !level.hasChunkAt(at.below())) {
                    status(level, worker, "Нужен загруженный участок шахты");
                    return false;
                }
                if (!level.getFluidState(at).isEmpty()) {
                    status(level, worker, "Вода или лава в слое шахты: проходка остановлена");
                    return false;
                }
            }
            if (layer > 0 && level.getBlockState(ladderCell).isAir()) {
                var route = KingdomMineStructureWork.ladderAt(level, npc, worker, area, ladderCell);
                if (route != KingdomMineStructureWork.Result.READY) {
                    status(level, worker, route == KingdomMineStructureWork.Result.BUILT
                            ? "Продлевает лестницу спуска"
                            : route == KingdomMineStructureWork.Result.NEED_MATERIAL
                                ? "Нужны лестницы на складе сырья"
                                : route == KingdomMineStructureWork.Result.WALKING
                                    ? "Несёт лестницы к спуску"
                                    : "Нет безопасной стены для лестницы спуска");
                    return route == KingdomMineStructureWork.Result.BUILT;
                }
            }
            for (KingdomMineGalleryPlan.Cell cell : cells) {
                BlockPos target = cell.target();
                if (!level.hasChunkAt(target)) {
                    status(level, worker, "Нужен загруженный участок шахты");
                    return false;
                }
                BlockState state = level.getBlockState(target);
                if (cleared(area, layer, target, state)) continue;
                BlockPos approach = approach(level, npc, target, cell.approach());
                if (approach == null) {
                    status(level, worker, "Нет безопасного места для добычи: " + target.toShortString());
                    return false;
                }
                // The support of TFC comes before the block that would fall.
                var safety = KingdomMineSupportSafety.inspect(level, target);
                if (safety == KingdomMineSupportSafety.Result.UNLOADED) {
                    status(level, worker, "Нужен загруженный участок шахты");
                    return false;
                }
                if (safety == KingdomMineSupportSafety.Result.NEEDS_HORIZONTAL_BEAM) {
                    if (!support(level, npc, worker, approach, target)) return false;
                    if (KingdomMineSupportSafety.inspect(level, target)
                            != KingdomMineSupportSafety.Result.SAFE) return false;
                }
                // The tool of the resident: the ground wants a shovel, the
                // rock and the ore want a pickaxe.
                var needed = KingdomMineTools.required(state);
                if (needed == KingdomMineTools.Tool.BLOCKED) {
                    status(level, worker, "Неподходящий блок для проходки: " + name(state));
                    return false;
                }
                if (!KingdomMineTools.equip(level, npc, worker, needed)) {
                    status(level, worker, needed == KingdomMineTools.Tool.SHOVEL
                        ? "Нужна лопата для грунта на складе сырья"
                        : "Нужна кирка для камня на складе сырья");
                    return false;
                }
                if (!KingdomMineWork.safeToBreak(level, target, worker)) {
                    status(level, worker, "Проходка остановлена: опасная или защищённая порода");
                    return false;
                }
                // The miner must stand next to the block and on solid ground.
                if (!standing(level, approach)) {
                    status(level, worker, "Нет безопасного места для добычи: " + approach.toShortString());
                    return false;
                }
                if (npc.distanceToSqr(approach.getX() + 0.5, approach.getY(),
                        approach.getZ() + 0.5) > REACH_SQR) {
                    var movement = KingdomActionEngine.navigate(level, npc, approach, 0.75);
                    status(level, worker, movement.message());
                    return false;
                }
                ItemStack tool = KingdomActionEngine.toolOf(npc, worker, needed);
                if (tool.isEmpty()) {
                    status(level, worker, "Нет исправного инструмента в сумке жителя");
                    return false;
                }
                var outcome = KingdomActionEngine.breakBlock(level, npc, worker, target, tool);
                if (!outcome.done()) {
                    status(level, worker, outcome.message());
                    return false;
                }
                KingdomNpcData.get(level.getServer()).setDirty();
                KingdomResidentDevelopment.get(level.getServer())
                        .award(worker.entityId, "mining", 1);
                status(level, worker, "Добывает " + name(state) + " на глубине Y=" + target.getY());
                return true;
            }
            // Native callbacks may change an earlier cell (e.g. falling debris).
            // Re-read all positions before committing the downward transition.
            if (!complete(area, layer, level::getBlockState)) {
                status(level, worker, "Проверяет оставшиеся блоки текущего слоя");
                return false;
            }
            // The surface cap needs no ladder: its supporting side wall may
            // not exist above ground. Descending layers extend the real ladder.
            if (layer == 0) continue;
            // The layer is clear: the descent gets its ladder and its deck.
            BlockPos descent = KingdomMineCentralShaftPlan.descent(area, layer);
            if (descent == null || !level.hasChunkAt(descent)) {
                status(level, worker, "Нужен загруженный участок спуска");
                return false;
            }
            if (descent != null) {
                var ladder = KingdomMineStructureWork.ladderAt(level, npc, worker, area, descent);
                if (ladder == KingdomMineStructureWork.Result.BUILT) {
                    KingdomNpcData.get(level.getServer()).setDirty();
                    status(level, worker, "Устанавливает лестницу на глубине " + layer);
                    return true;
                }
                if (ladder != KingdomMineStructureWork.Result.READY) {
                    status(level, worker, ladder == KingdomMineStructureWork.Result.NEED_MATERIAL
                        ? "Нужны лестницы на складе сырья"
                        : ladder == KingdomMineStructureWork.Result.WALKING
                            ? "Идёт за лестницами"
                            : "Невозможно установить лестницу спуска");
                    return false;
                }
                // The deck of planks gives the miner a solid floor at the descent.
                BlockPos deck = descent.east();
                if (KingdomMineStructurePlan.isLevel(layer) && level.hasChunkAt(deck)) {
                    var plank = KingdomMineStructureWork.plankAt(level, npc, worker, area, deck);
                    if (plank == KingdomMineStructureWork.Result.BUILT) {
                        KingdomNpcData.get(level.getServer()).setDirty();
                        status(level, worker, "Укладывает настил спуска на глубине " + layer);
                        return true;
                    }
                    if (plank != KingdomMineStructureWork.Result.READY) {
                        status(level, worker, switch (plank) {
                            case NEED_MATERIAL -> "Нужны доски для настила на складе сырья";
                            case WALKING -> "Идёт за досками для настила";
                            case NEED_TOOL -> "Нужен инструмент, чтобы расчистить клетку настила";
                            default -> "Невозможно уложить настил спуска: "
                                + KingdomMineStructureWork.lastReason();
                        });
                        return false;
                    }
                }
            }
        }
        status(level, worker, "Все слои шахты пройдены");
        return false;
    }

    static boolean complete(KingdomMineSettings.Shaft area, int layer,
                            java.util.function.Function<BlockPos, BlockState> world) {
        boolean result = true;
        for (var cell : KingdomMineCentralShaftPlan.layer(area, layer)) {
            BlockState state = world.apply(cell.target());
            result &= state.getFluidState().isEmpty() && cleared(area, layer, cell.target(), state);
        }
        return result;
    }

    static boolean cleared(KingdomMineSettings.Shaft area, int layer,
                           BlockPos at, BlockState state) {
        if (state.isAir()) return true;
        BlockPos descent = KingdomMineCentralShaftPlan.descent(area, layer);
        if (at.equals(descent) && state.is(Blocks.LADDER)
                && state.getValue(LadderBlock.FACING) == Direction.NORTH) return true;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if ((at.equals(descent.east()) || at.equals(descent.north()))
                && KingdomMineStructureWork.isDeckBlock(state)) return true;
        // Native TFC frames built during excavation are infrastructure, not ore.
        return id.startsWith("tfc:wood/vertical_support/")
                || KingdomMineSupportSafety.isTfcHorizontalSupport(state);
    }

    private static boolean standing(ServerLevel level, BlockPos at) {
        if (!level.hasChunkAt(at) || !level.hasChunkAt(at.above())
                || !level.hasChunkAt(at.below())) return false;
        return KingdomMineMaintenance.passable(level.getBlockState(at))
                && KingdomMineMaintenance.passable(level.getBlockState(at.above()))
                && level.getFluidState(at).isEmpty() && level.getFluidState(at.above()).isEmpty()
                && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP);
    }

    private static BlockPos approach(ServerLevel level, Villager npc, BlockPos target,
                                     BlockPos planned) {
        java.util.ArrayList<BlockPos> candidates = new java.util.ArrayList<>();
        candidates.add(planned);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(target.relative(direction));
            candidates.add(target.relative(direction).above());
            candidates.add(target.relative(direction, 2));
            candidates.add(target.relative(direction, 2).above());
        }
        // Never dig away the floor under the miner's feet. The first cut can
        // use an adjacent surface square, and resumed/cave layers can route
        // around their missing serpentine predecessor.
        return candidates.stream().filter(at -> !at.below().equals(target))
                .filter(at -> standing(level, at))
                .filter(at -> {
                    Vec3 eye = new Vec3(at.getX()+.5, at.getY()+npc.getEyeHeight(), at.getZ()+.5);
                    return eye.distanceToSqr(Vec3.atCenterOf(target)) <= 20.25
                            && level.clip(new net.minecraft.world.level.ClipContext(eye,
                                Vec3.atCenterOf(target), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, npc))
                                    .getBlockPos().equals(target);
                })
                // Stable candidate order keeps an in-progress emergency
                // frame identical while the miner visits the supply depot.
                .filter(at -> npc.distanceToSqr(at.getX()+.5, at.getY(), at.getZ()+.5) <= REACH_SQR
                        || npc.getNavigation().createPath(at, 0) != null)
                .findFirst().orElse(null);
    }

    /**
     * Prepares the real support of TFC before a block that would fall is dug.
     *
     * @return true when the work may continue.
     */
    private static boolean support(ServerLevel level, Villager npc, KingdomNpcData.Worker worker,
                                   BlockPos previous, BlockPos floorCell) {
        var frame = KingdomMineSupportPlan.emergency(previous, floorCell);
        if (frame == null) {
            status(level, worker, "Опасная порода: нет безопасного места для установки опор TFC");
            return false;
        }
        var preparation = KingdomMineSupportExcavation.tick(level, npc, worker, frame);
        if (preparation == KingdomMineSupportExcavation.Result.EXCAVATED) {
            status(level, worker, "Готовит безопасное крепление перед проходкой: "
                + KingdomMineSupportExcavation.lastDetail());
            return false;
        }
        if (preparation == KingdomMineSupportExcavation.Result.WALKING) {
            status(level, worker, "Идёт укреплять опасный участок: "
                + KingdomMineSupportExcavation.lastDetail());
            return false;
        }
        if (preparation != KingdomMineSupportExcavation.Result.CLEAR) {
            status(level, worker, "Карманы опор недоступны: проходка запрещена");
            return false;
        }
        var support = KingdomMineSupportBuilder.tick(level, npc, worker, frame);
        switch (support) {
            case SUPPLIED -> {
                status(level, worker, "Взял балки TFC со склада, несёт к креплению");
                return false;
            }
            case PLACED -> {
                status(level, worker, "Укрепляет участок перед добычей");
                return false;
            }
            case WALKING -> {
                status(level, worker, "Несёт крепления к опасному участку");
                return false;
            }
            case NO_STOCK -> {
                status(level, worker, "Нужны 8 балок TFC одной породы");
                return false;
            }
            case INACCESSIBLE -> {
                status(level, worker, "Нет доступа к месту установки креплений");
                return false;
            }
            case NEED_CLEAR_POCKETS -> {
                status(level, worker, "Крепление: нужны безопасные карманы под стойки");
                return false;
            }
            case FAILED_NATIVE_PLACEMENT -> {
                status(level, worker, "TFC отклонил установку опоры: проходка запрещена");
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** Whether a planned shaft cell already contains constructed access. */
    static boolean completedStructure(KingdomMineSettings.Shaft area, int layer,
                                      BlockPos target, BlockState state) {
        if (area == null || target == null || state == null) return false;
        BlockPos descent = KingdomMineCentralShaftPlan.descent(area, layer);
        // The supports of TFC are the finished structures of the mine: the
        // plan never digs them out again.
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).toString();
        if (id.startsWith("tfc:wood/vertical_support/")
                || id.startsWith("tfc:wood/horizontal_support/")) return true;
        return (target.equals(descent)
                && state.is(net.minecraft.world.level.block.Blocks.LADDER))
            || (target.equals(descent.east())
                && KingdomMineStructureWork.isDeckBlock(state));
    }

    /** The diagnosis of the work, written only when it changes. */
    private static void status(ServerLevel level, KingdomNpcData.Worker worker, String next) {
        if (next.equals(worker.mineStatus)) return;
        worker.mineStatus = next;
        KingdomNpcData.get(level.getServer()).setDirty();
    }
}
