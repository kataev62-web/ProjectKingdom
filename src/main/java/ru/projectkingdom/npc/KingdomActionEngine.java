package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * The executor of the physical actions of a resident.
 *
 * <p>Every operation of the world goes through this class: the navigation, the
 * breaking of a block, the placing of a block and the collection of the items.
 * The executor never invents an item and never moves a container without the
 * right of the owner.
 *
 * <p>The breaking of a block follows the vanilla path of a player: the Forge
 * break event is fired first, so that the protection of TFC, the collapse
 * rules and every other mod can refuse the block, and only then the block is
 * destroyed. A direct replacement of the block with air would skip all of
 * that.
 */
public final class KingdomActionEngine {
    private KingdomActionEngine() {}

    /** The result of one physical action. */
    public enum Status { DONE, MOVING, BLOCKED, FAILED }

    /** The result of one physical action with the reason for the operator. */
    public record Outcome(Status status, String message) {
        public boolean done() { return status == Status.DONE; }
        public boolean failed() { return status == Status.FAILED || status == Status.BLOCKED; }
    }

    /** The distance in which the resident counts as arrived. */
    public static final double REACH = 0.75;
    /** The squared distance of the reach of a resident. */
    public static final double REACH_SQR = REACH * REACH;

    /** The progress of the navigation of one resident. */
    private static final class Nav {
        BlockPos target;
        double bestDistance = Double.MAX_VALUE;
        int stuckTicks;
        int ticks;
    }

    private static final Map<UUID, Nav> NAVIGATIONS = new HashMap<>();

    /**
     * The tool of the resident.
     *
     * <p>The tool comes from the working view of the belongings, because the
     * view holds the very stack that the container holds: the wear of the tool
     * must change the physical item and not a copy of it. The container is
     * only the fallback for a resident that has no working view yet.
     */
    public static ItemStack toolOf(Villager resident, KingdomNpcData.Worker worker,
                                   KingdomMineTools.Tool needed) {
        if (worker != null) {
            if (KingdomMineTools.matches(worker.fieldTool, needed)) return worker.fieldTool;
            for (ItemStack stack : worker.fieldInventory)
                if (KingdomMineTools.matches(stack, needed)) return stack;
        }
        Container personal = KingdomResidentInventory.personal(resident);
        ItemStack real = KingdomResidentInventory.findTool(personal, needed);
        return real;
    }

    /**
     * Walks the resident to a position.
     *
     * <p>The navigation of MCA is not touched: the resident walks because the
     * Kingdom ordered it, and the arbitration of the shift keeps MCA from
     * ordering something else at the same time.
     */
    public static Outcome navigate(ServerLevel level, Villager resident, BlockPos target, double speed) {
        if (resident == null || target == null) return new Outcome(Status.FAILED, "нет жителя или цели");
        if (!level.hasChunkAt(target)) return new Outcome(Status.BLOCKED, "цель в незагруженном чанке");
        Nav nav = NAVIGATIONS.computeIfAbsent(resident.getUUID(), id -> new Nav());
        if (!target.equals(nav.target)) {
            nav = new Nav();
            nav.target = target.immutable();
            NAVIGATIONS.put(resident.getUUID(), nav);
        }
        double distance = resident.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (distance <= REACH_SQR) {
            NAVIGATIONS.remove(resident.getUUID());
            return new Outcome(Status.DONE, "на месте");
        }
        nav.ticks++;
        if (distance < nav.bestDistance - 0.01) {
            nav.bestDistance = distance;
            nav.stuckTicks = 0;
        } else {
            nav.stuckTicks++;
        }
        if (nav.stuckTicks > 60) {
            NAVIGATIONS.remove(resident.getUUID());
            return new Outcome(Status.FAILED, "путь не найден");
        }
        if (nav.ticks > 1200) {
            NAVIGATIONS.remove(resident.getUUID());
            return new Outcome(Status.FAILED, "слишком долго идёт");
        }
        // The coordinate moveTo overload uses path accuracy=1. That can
        // report an adjacent starting square as the completed path even when
        // the safe work stance must be reached to within 0.75 blocks.
        var path = resident.getNavigation().createPath(target, 0);
        if (!reaches(path, target))
            return new Outcome(Status.BLOCKED, "нет пути к безопасному месту добычи");
        if (!resident.getNavigation().moveTo(path, speed))
            return new Outcome(Status.FAILED, "не удалось начать движение");
        return new Outcome(Status.MOVING, "идёт к " + target.toShortString());
    }

    static boolean reaches(net.minecraft.world.level.pathfinder.Path path, BlockPos target) {
        return path != null && path.canReach() && path.getEndNode() != null
                && path.getEndNode().asBlockPos().equals(target);
    }

    /** Whether the resident stands close enough to work at a position. */
    public static boolean inReach(Villager resident, BlockPos target) {
        if (resident == null || target == null) return false;
        return resident.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= 20.25;
    }

    /**
     * Breaks one real block with the real tool of the resident.
     *
     * <p>Native hardness and Forge break-speed hooks govern progress. The
     * server player game mode performs destruction, tool wear and native loot.
     */
    public static Outcome breakBlock(ServerLevel level, Villager resident, KingdomNpcData.Worker worker,
                                      BlockPos target, ItemStack tool) {
        if (level == null || resident == null || target == null) return new Outcome(Status.FAILED, "нет данных");
        if (!level.hasChunkAt(target)) return new Outcome(Status.BLOCKED, "чанк не загружен");
        BlockState state = level.getBlockState(target);
        if (state.isAir()) return new Outcome(Status.DONE, "блока уже нет");
        if (tool == null || tool.isEmpty())
            return new Outcome(Status.BLOCKED, "нет инструмента");
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))
            return new Outcome(Status.BLOCKED, "инструмент не подходит к блоку");
        if (!inReach(resident, target)) return new Outcome(Status.MOVING, "не дотягивается до блока");
        // The protection of the world: the right of the owner, the liquid, the
        // block entity and the falling block are checked before anything moves.
        if (worker != null && !KingdomMineWork.safeToBreak(level, target, worker))
            return new Outcome(Status.BLOCKED, "блок защищён или опасен");
        if (worker == null || worker.employer == null)
            return new Outcome(Status.BLOCKED, "нет контекста владельца");
        // FakePlayerFactory's Minecraft identity must never inherit another
        // worker's authority. Protection mods see the employer's real UUID.
        FakePlayer actor = FakePlayerFactory.get(level,
                new GameProfile(worker.employer, "KingdomWorker"));
        Context context = Context.of(actor); // capture BEFORE any mutation
        boolean installedTool = false;
        try {
            actor.getInventory().clearContent();
            actor.getInventory().selected = 0;
            actor.setPos(resident.getX(), resident.getY(), resident.getZ());
            actor.setOnGround(resident.onGround());
            actor.setGameMode(GameType.SURVIVAL);
            actor.setItemInHand(InteractionHand.MAIN_HAND, tool);
            installedTool = true;
            var sight = level.clip(new net.minecraft.world.level.ClipContext(
                    resident.getEyePosition(), Vec3.atCenterOf(target),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, resident));
            if (!sight.getBlockPos().equals(target))
                return new Outcome(Status.BLOCKED, "блок закрыт препятствием");
            float speed = state.getDestroyProgress(actor, level, target);
            if (!Float.isFinite(speed) || speed <= 0)
                return new Outcome(Status.BLOCKED, "блок нельзя добыть этим инструментом");
            Dig dig = DIGS.get(resident.getUUID());
            long now = level.getGameTime();
            if (dig == null || !dig.target.equals(target) || !dig.state.equals(state)
                    || !ItemStack.isSameItemSameTags(dig.tool, tool) || now - dig.lastTick > 80) {
                var click = net.minecraftforge.common.ForgeHooks.onLeftClickBlock(actor, target,
                        net.minecraft.core.Direction.UP);
                if (click.isCanceled() || click.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY) {
                    DIGS.remove(resident.getUUID());
                    return new Outcome(Status.BLOCKED, "добыча отменена защитой");
                }
                if (click.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY)
                    state.attack(level, target, actor);
                dig = new Dig(target.immutable(), state, tool, now);
                DIGS.put(resident.getUUID(), dig);
            }
            long elapsed = Math.max(0, Math.min(40, now - dig.lastTick));
            dig.progress += speed * elapsed;
            dig.lastTick = now;
            if (dig.progress < 1)
                return new Outcome(Status.MOVING, "Добывает блок");
            DIGS.remove(resident.getUUID());
            // This native player path owns BreakEvent, canHarvestBlock,
            // onDestroyedByPlayer, mineBlock/durability and playerDestroy/TFC loot.
            // Never calculate drops or apply a second point of tool wear here.
            var bounds = new net.minecraft.world.phys.AABB(target).inflate(2);
            var before = level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            if (!actor.gameMode.destroyBlock(target))
                return new Outcome(Status.BLOCKED, "разрушение отменено защитой");
            int gathered = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds,
                    e -> e.isAlive() && !before.contains(e.getUUID()))) {
                ItemStack drop = item.getItem();
                if (!NpcFieldWork.canFit(worker, List.of(drop))) continue;
                gathered += drop.getCount();
                NpcFieldWork.put(worker, drop.copy());
                item.discard();
            }
            worker.fieldHarvested += gathered;
            worker.skillXp++;
            worker.energy = Math.max(0, worker.energy - 1);
            KingdomNpcData.get(level.getServer()).setDirty();
            return new Outcome(Status.DONE, "добыл " + gathered);
        } finally {
            // Native mineBlock can replace a broken stack rather than mutate it.
            ItemStack remaining = actor.getItemInHand(InteractionHand.MAIN_HAND);
            if (installedTool && remaining != tool && worker.fieldTool == tool)
                worker.fieldTool = remaining.copy();
            context.restore(actor);
        }
    }

    private static final class Dig {
        final BlockPos target;
        final BlockState state;
        final ItemStack tool;
        long lastTick;
        float progress;
        Dig(BlockPos target, BlockState state, ItemStack tool, long tick) {
            this.target = target; this.state = state; this.tool = tool.copy(); this.lastTick = tick;
        }
    }
    private static final Map<UUID, Dig> DIGS = new HashMap<>();

    /** Full snapshot of the reusable Forge actor, including hotbar and offhand. */
    private record Context(List<ItemStack> inventory, int selected, Vec3 position,
                           Vec3 movement, float yaw, float pitch, boolean onGround,
                           GameType gameMode, net.minecraft.nbt.CompoundTag abilities,
                           boolean sneaking) {
        static Context of(FakePlayer actor) {
            List<ItemStack> items = new java.util.ArrayList<>();
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++)
                items.add(actor.getInventory().getItem(slot).copy());
            var abilities = new net.minecraft.nbt.CompoundTag();
            actor.getAbilities().addSaveData(abilities);
            return new Context(items, actor.getInventory().selected, actor.position(),
                    actor.getDeltaMovement(), actor.getYRot(), actor.getXRot(), actor.onGround(),
                    actor.gameMode.getGameModeForPlayer(), abilities, actor.isShiftKeyDown());
        }
        void restore(FakePlayer actor) {
            actor.getInventory().clearContent();
            for (int slot = 0; slot < inventory.size(); slot++)
                actor.getInventory().setItem(slot, inventory.get(slot).copy());
            actor.getInventory().selected = selected;
            actor.setPos(position);
            actor.setDeltaMovement(movement);
            actor.setYRot(yaw);
            actor.setXRot(pitch);
            actor.setOnGround(onGround);
            actor.setGameMode(gameMode);
            actor.getAbilities().loadSaveData(abilities);
            actor.setShiftKeyDown(sneaking);
        }
    }

    /** Native placement with the same owner/protection context as excavation. */
    public static Outcome placeBlock(ServerLevel level, Villager resident,
                                     KingdomNpcData.Worker worker, BlockPos target,
                                     ItemStack material, net.minecraft.core.Direction supportDirection) {
        if (level == null || resident == null || worker == null || worker.employer == null
                || target == null || material == null || material.isEmpty())
            return new Outcome(Status.BLOCKED, "нет материала или владельца");
        BlockPos support = target.relative(supportDirection);
        if (!level.hasChunkAt(target) || !level.hasChunkAt(support)
                || !level.getFluidState(target).isEmpty()
                || !level.getBlockState(target).canBeReplaced()
                || !worker.employer.equals(KingdomTerritories.get(level.getServer()).owner(level, target)))
            return new Outcome(Status.BLOCKED, "нет безопасного места установки");
        if (!inReach(resident, target)) return new Outcome(Status.MOVING, "не дотягивается до установки");
        FakePlayer actor = FakePlayerFactory.get(level,
                new GameProfile(worker.employer, "KingdomWorker"));
        Context context = Context.of(actor);
        try {
            actor.getInventory().clearContent();
            actor.getInventory().selected = 0;
            actor.setPos(resident.position());
            actor.setShiftKeyDown(false); // TFC native placement builds complete support columns.
            actor.setGameMode(GameType.SURVIVAL);
            actor.setItemInHand(InteractionHand.MAIN_HAND, material);
            BlockState before = level.getBlockState(target);
            var face = supportDirection.getOpposite();
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    Vec3.atCenterOf(support).add(face.getStepX()*.5, face.getStepY()*.5, face.getStepZ()*.5),
                    face, support, false);
            actor.gameMode.useItemOn(actor, level, material, InteractionHand.MAIN_HAND, hit);
            return !level.getBlockState(target).equals(before)
                    ? new Outcome(Status.DONE, "установил блок")
                    : new Outcome(Status.BLOCKED, "установка отклонена");
        } finally {
            context.restore(actor);
        }
    }

    /** Collects delayed native/merged drops into the bound work view. */
    static int pickup(ServerLevel level, Villager resident, KingdomNpcData.Worker worker, double radius) {
        if (worker.employer == null) return 0;
        int gathered = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,
                resident.getBoundingBox().inflate(radius), e -> e.isAlive() && !e.hasPickUpDelay())) {
            if (!worker.employer.equals(KingdomTerritories.get(level.getServer())
                    .owner(level, entity.blockPosition()))) continue;
            ItemStack stack = entity.getItem();
            if (!NpcFieldWork.canFit(worker, List.of(stack))) continue;
            NpcFieldWork.put(worker, stack.copy());
            gathered += stack.getCount();
            entity.discard();
        }
        if (gathered > 0) {
            worker.fieldHarvested += gathered;
            KingdomNpcData.get(level.getServer()).setDirty();
        }
        return gathered;
    }

    /**
     * Collects the items that lie near the resident into the real inventory.
     *
     * @return the number of collected items.
     */
    public static int pickup(ServerLevel level, Villager resident, double radius) {
        if (level == null || resident == null) return 0;
        Container personal = KingdomResidentInventory.personal(resident);
        int taken = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                resident.getBoundingBox().inflate(radius), e -> e.isAlive() && !e.hasPickUpDelay())) {
            if (personal == null) break;
            ItemStack stack = item.getItem().copy();
            if (stack.isEmpty()) continue;
            int moved = KingdomResidentInventory.insertCount(personal, stack);
            if (moved <= 0) continue;
            // Only what really entered the bag is counted and only what was
            // taken away disappears from the world.
            taken += moved;
            if (stack.isEmpty()) item.discard();
            else item.setItem(stack);
        }
        return taken;
    }

    /** Forgets the navigation progress of a resident. */
    public static void forget(UUID resident) {
        if (resident != null) { NAVIGATIONS.remove(resident); DIGS.remove(resident); }
    }

    /** Forgets every navigation, for example when the server stops. */
    public static void forgetAll() { NAVIGATIONS.clear(); DIGS.clear(); }
}
