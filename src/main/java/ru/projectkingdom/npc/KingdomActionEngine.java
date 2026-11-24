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
import net.minecraft.world.level.block.Block;
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
    public static final double REACH = 1.6;
    /** The squared distance of the reach of a resident. */
    public static final double REACH_SQR = REACH * REACH;

    /** The progress of the navigation of one resident. */
    private static final class Nav {
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
        resident.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        return new Outcome(Status.MOVING, "идёт к " + target.toShortString());
    }

    /** Whether the resident stands close enough to work at a position. */
    public static boolean inReach(Villager resident, BlockPos target) {
        if (resident == null || target == null) return false;
        return resident.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= 36.0;
    }

    /**
     * Breaks one real block with the real tool of the resident.
     *
     * <p>The path is the path of a player: the Forge break event first, the
     * drops of the real tool, then the destruction of the block and the wear
     * of the tool exactly once. The resident keeps the drops.
     */
    public static Outcome breakBlock(ServerLevel level, Villager resident, KingdomNpcData.Worker worker,
                                      BlockPos target, ItemStack tool) {
        if (level == null || resident == null || target == null) return new Outcome(Status.FAILED, "нет данных");
        if (!level.hasChunkAt(target)) return new Outcome(Status.BLOCKED, "чанк не загружен");
        BlockState state = level.getBlockState(target);
        if (state.isAir()) return new Outcome(Status.DONE, "блока уже нет");
        if (tool == null || tool.isEmpty())
            return new Outcome(Status.BLOCKED, "нет инструмента");
        if (!tool.isCorrectToolForDrops(state))
            return new Outcome(Status.BLOCKED, "инструмент не подходит к блоку");
        if (!inReach(resident, target)) return new Outcome(Status.MOVING, "не дотягивается до блока");
        // The protection of the world: the right of the owner, the liquid, the
        // block entity and the falling block are checked before anything moves.
        if (worker != null && !KingdomMineWork.safeToBreak(level, target, worker))
            return new Outcome(Status.BLOCKED, "блок защищён или опасен");
        FakePlayer actor = actor(level, resident, worker, tool);
        Context context = Context.of(actor);
        try {
            // The vanilla path of a player: the break event of Forge is fired
            // first, so that TFC, the collapse rules and every other mod may
            // refuse the block before it disappears. The event is fired once.
            net.minecraftforge.event.level.BlockEvent.BreakEvent event =
                    new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, target, state, actor);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled())
                return new Outcome(Status.BLOCKED, "разрушение отменено защитой");
            // The drops are computed with the real tool of the resident.
            List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), actor, tool);
            if (!level.destroyBlock(target, false))
                return new Outcome(Status.FAILED, "блок не разрушен");
            // The block is gone, so the drops are given exactly once. The
            // block is not broken again, so nothing is given twice.
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                if (!give(worker, drop)) {
                    // The bag is full: the rest of the drops stays in the world.
                    Block.popResource(level, target, drop.copy());
                }
            }
            // The wear belongs to the physical tool: the very stack that the
            // container holds is the one that is used up.
            tool.hurt(1, level.random, null);
            if (tool.isEmpty() || tool.getDamageValue() >= tool.getMaxDamage()) {
                tool.setCount(0);
                if (worker != null && worker.fieldTool == tool) worker.fieldTool = ItemStack.EMPTY;
            }
            if (worker != null) {
                worker.fieldHarvested += drops.stream().mapToInt(ItemStack::getCount).sum();
                worker.skillXp++;
                worker.energy = Math.max(0, worker.energy - 1);
                KingdomNpcData.get(level.getServer()).setDirty();
            }
            return new Outcome(Status.DONE, "добыл " + drops.stream().mapToInt(ItemStack::getCount).sum());
        } finally {
            // The context of the operation leaves no trace in the shared
            // instance: neither the tool nor anything else.
            context.restore(actor);
        }
    }

    /** The state of the shared context of an operation, so that nothing is left behind. */
    private static final class Context {
        private final ItemStack hand;
        private final List<ItemStack> inventory;

        private Context(ItemStack hand, List<ItemStack> inventory) {
            this.hand = hand;
            this.inventory = inventory;
        }

        static Context of(FakePlayer actor) {
            List<ItemStack> items = new java.util.ArrayList<>();
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++)
                items.add(actor.getInventory().getItem(slot).copy());
            return new Context(actor.getItemInHand(InteractionHand.MAIN_HAND).copy(), items);
        }

        void restore(FakePlayer actor) {
            actor.setItemInHand(InteractionHand.MAIN_HAND, hand.copy());
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++)
                actor.getInventory().setItem(slot,
                        slot < inventory.size() ? inventory.get(slot).copy() : ItemStack.EMPTY);
            actor.getInventory().clearContent();
            for (int slot = 0; slot < inventory.size(); slot++)
                actor.getInventory().setItem(slot, inventory.get(slot).copy());
        }
    }

    /**
     * Places a real block from the real inventory of the resident.
     */
    public static Outcome placeBlock(ServerLevel level, Villager resident, BlockPos target, ItemStack material) {
        if (level == null || resident == null || target == null) return new Outcome(Status.FAILED, "нет данных");
        if (!level.hasChunkAt(target)) return new Outcome(Status.BLOCKED, "чанк не загружен");
        if (!level.getBlockState(target).canBeReplaced()) return new Outcome(Status.BLOCKED, "место занято");
        if (material == null || material.isEmpty()) return new Outcome(Status.BLOCKED, "нет материала");
        if (!(material.getItem() instanceof net.minecraft.world.item.BlockItem blockItem))
            return new Outcome(Status.BLOCKED, "предмет не является блоком");
        BlockState placed = blockItem.getBlock().defaultBlockState();
        if (placed.isAir()) return new Outcome(Status.BLOCKED, "предмет не является блоком");
        if (!level.setBlock(target, placed, 3)) return new Outcome(Status.FAILED, "блок не установлен");
        material.shrink(1);
        return new Outcome(Status.DONE, "установил блок");
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

    /** Gives the drops to the resident: the Kingdom mirror of the bag. */
    private static boolean give(KingdomNpcData.Worker worker, ItemStack drop) {
        if (worker == null) return false;
        if (!NpcFieldWork.canFit(worker, List.of(drop))) return false;
        NpcFieldWork.put(worker, drop);
        return true;
    }

    /**
     * The context of the operation.
     *
     * <p>The break event of Forge expects a player. A short living fake player
     * is used as the context of the operation: it holds the real tool of the
     * resident for the duration of the call and holds no persistent inventory.
     */
    private static final Map<UUID, FakePlayer> ACTORS = new HashMap<>();

    /**
     * The context of the operation for a resident.
     *
     * <p>The context carries the identity of the employer of the resident:
     * the protection of the land and the rights of the settlement are
     * checked against the employer, and the resident works on the land of
     * the employer. A context without an identity would be a stranger and
     * every break would be refused.
     */
    private static FakePlayer actor(ServerLevel level, Villager resident,
                                    KingdomNpcData.Worker worker, ItemStack tool) {
        UUID owner = worker == null ? null : worker.employer;
        if (owner == null) owner = resident.getUUID();
        FakePlayer actor = ACTORS.get(owner);
        if (actor == null || actor.level() != level) {
            actor = FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(owner, "KingdomWorker"));
            ACTORS.put(owner, actor);
        }
        actor.setPos(resident.getX(), resident.getY(), resident.getZ());
        // The tool of the resident is the tool of the operation, so that TFC
        // and every other mod may read it. It is a copy: the wear belongs to
        // the physical tool of the resident and is applied there.
        actor.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        return actor;
    }

    /** Forgets the navigation progress of a resident. */
    public static void forget(UUID resident) {
        if (resident != null) NAVIGATIONS.remove(resident);
    }

    /** Forgets every navigation, for example when the server stops. */
    public static void forgetAll() { NAVIGATIONS.clear(); ACTORS.clear(); }
}
