package ru.projectkingdom.npc;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
    private static final double REACH_SQR = 6.25;

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
        // The surface of the footprint is the first layer: the cap is cleared
        // before the excavation goes deeper, so that TFC never has to refuse
        // a block that would hang in the air.
        for (int layer = 0; layer <= maxDepth; layer++) {
            List<KingdomMineGalleryPlan.Cell> cells =
                    KingdomMineCentralShaftPlan.layer(area, layer);
            for (KingdomMineGalleryPlan.Cell cell : cells) {
                BlockPos target = cell.target();
                BlockPos approach = cell.approach();
                if (!level.hasChunkAt(target) || !level.hasChunkAt(approach)) {
                    status(level, worker, "Нужен загруженный участок шахты");
                    return false;
                }
                BlockState state = level.getBlockState(target);
                if (state.isAir()) continue;
                // Finished descent structures belong to this layer's mining
                // footprint but must NOT be mistaken for unmined rock on the
                // next tick. Only skip their exact planned cells.
                if (completedStructure(area, layer, target, state)) continue;
                // The support of TFC comes before the block that would fall.
                var safety = KingdomMineSupportSafety.inspect(level, target);
                if (safety == KingdomMineSupportSafety.Result.UNLOADED) {
                    status(level, worker, "Нужен загруженный участок шахты");
                    return false;
                }
                if (safety == KingdomMineSupportSafety.Result.NEEDS_HORIZONTAL_BEAM) {
                    if (!support(level, npc, worker, approach, target)) return false;
                    continue;
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
                if (!KingdomMineMaintenance.passable(level.getBlockState(approach))) {
                    status(level, worker, "Нет безопасного места для добычи: " + approach.toShortString());
                    return false;
                }
                // The miner must stand on something solid. The hole under the
                // place of the miner is allowed when it is the block it digs.
                if (level.getBlockState(approach.below()).isAir()
                        && !approach.below().equals(target)) {
                    status(level, worker, "Нет безопасного места для добычи: " + approach.toShortString());
                    return false;
                }
                if (npc.distanceToSqr(approach.getX() + 0.5, approach.getY(),
                        approach.getZ() + 0.5) > REACH_SQR) {
                    npc.getNavigation().moveTo(approach.getX() + 0.5, approach.getY(),
                            approach.getZ() + 0.5, 0.75);
                    status(level, worker, "Идёт к месту добычи");
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
            // The layer is clear: the descent gets its ladder and its deck.
            BlockPos descent = KingdomMineCentralShaftPlan.descent(area, layer);
            if (descent != null && level.hasChunkAt(descent)) {
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
                BlockPos deck = descent.north();
                if (level.hasChunkAt(deck)) {
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
            status(level, worker, "Карманы опор недоступны; продолжает безопасную проходку: "
                + KingdomMineSupportExcavation.lastDetail());
            return true;
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
        return (target.equals(descent)
                && state.is(net.minecraft.world.level.block.Blocks.LADDER))
            || (target.equals(descent.north())
                && KingdomMineStructureWork.isDeckBlock(state));
    }

    /** The diagnosis of the work, written only when it changes. */
    private static void status(ServerLevel level, KingdomNpcData.Worker worker, String next) {
        if (next.equals(worker.mineStatus)) return;
        worker.mineStatus = next;
        KingdomNpcData.get(level.getServer()).setDirty();
    }
}
