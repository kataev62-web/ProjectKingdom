package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

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
        NO_MATERIALS("На складе нет нужного материала"),
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
        var kind = KingdomBuildingTypes.type(project.design());
        if (kind == null) return new Step(Result.INVALID_BLOCK, null, "");
        var blocks = KingdomBuildingTypes.design(kind).blocks();
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
        if (next.result() != Result.PLACED) return next;
        if (project.status() == KingdomConstructionData.Status.CANCELLED)
            return new Step(Result.CANCELLED, next.target(), next.material());
        if (!project.dimension().equals(level.dimension().location().toString()))
            return new Step(Result.UNLOADED, next.target(), next.material());

        BlockPos marker = new BlockPos(project.x() - 2, project.y(), project.z() - 2);
        if (!KingdomConstructionMarkers.isMarker(level, marker))
            return new Step(Result.NO_MARKER, next.target(), next.material());
        BlockPos target = next.target();
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()
                || !level.hasChunkAt(target))
            return new Step(Result.UNLOADED, target, next.material());
        if (!KingdomConstructionAccess.canBuild(server, level, target, project.owner()))
            return new Step(Result.NO_RIGHTS, target, next.material());

        ResourceLocation id = ResourceLocation.tryParse(next.material());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
            return new Step(Result.INVALID_BLOCK, target, next.material());
        var block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR || block.asItem() == Items.AIR)
            return new Step(Result.INVALID_BLOCK, target, next.material());
        if (level.getBlockState(target).is(block)) {
            advance(server, project);
            return new Step(Result.ALREADY_PRESENT, target, next.material());
        }
        if (!level.getBlockState(target).isAir())
            return new Step(Result.OBSTACLE, target, next.material());

        Container depot = depot(level, marker);
        if (depot == null) return new Step(Result.NO_DEPOT, target, next.material());
        Item item = block.asItem();
        int slot = -1;
        for (int i = 0; i < depot.getContainerSize(); i++) {
            var stack = depot.getItem(i);
            if (!stack.isEmpty() && stack.is(item) && depot.canTakeItem(depot, i, stack)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return new Step(Result.NO_MATERIALS, target, next.material());

        // The world and the inventory are mutated together on the single server thread.
        ItemStack material = depot.removeItem(slot, 1);
        if (material.isEmpty()) return new Step(Result.NO_MATERIALS, target, next.material());
        if (!level.setBlock(target, block.defaultBlockState(), 3)) {
            // Roll back the exact item. A failed restore is an integrity error,
            // not an excuse to silently delete or invent inventory.
            ItemStack current = depot.getItem(slot);
            if (current.isEmpty()) {
                depot.setItem(slot, material);
            } else if (ItemStack.isSameItemSameTags(current, material)
                    && current.getCount() < current.getMaxStackSize()) {
                current.grow(1);
            } else {
                throw new IllegalStateException("Kingdom: cannot restore building material at " + target);
            }
            depot.setChanged();
            return new Step(Result.OBSTACLE, target, next.material());
        }
        depot.setChanged();
        advance(server, project);
        return new Step(Result.PLACED, target, next.material());
    }

    private static void advance(MinecraftServer server, KingdomConstructionData.Project project) {
        var type = KingdomBuildingTypes.type(project.design());
        if (type == null) return;
        KingdomConstructionData.get(server).advance(project.id(), 1,
            KingdomBuildingTypes.design(type).blocks().size());
    }

    private static Container depot(ServerLevel level, BlockPos marker) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = marker.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            BlockEntity be = level.getBlockEntity(adjacent);
            if (be instanceof Container container) return container;
        }
        return null;
    }
}
