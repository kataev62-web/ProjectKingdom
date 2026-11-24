package ru.projectkingdom.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * The real inventory of a resident, opened by the employer.
 *
 * <p>For an MCA resident the menu shows the real container of MCA: the nine
 * rows of three slots that the villager carries and that MCA saves with the
 * entity. The employer puts the real shovel, the real pickaxe and the other
 * real items into it, and the resident uses exactly those items.
 *
 * <p>Without MCA the menu shows the Kingdom mirror of the bag, which is
 * written back when the menu closes. Exactly one viewer may edit a resident at
 * a time, and the physical work of the resident pauses while the menu is open.
 */
public final class KingdomNpcBagMenu extends ChestMenu {
    private static final Map<UUID, UUID> OPEN = new ConcurrentHashMap<>();
    private final ServerPlayer viewer;
    private final int npcId;
    private final UUID entityId;
    private final BagSlots bag;

    /** Whether the inventory of a resident is open. */
    public static boolean isOpen(UUID entity) {
        return entity != null && OPEN.containsKey(entity);
    }

    /**
     * Opens the inventory of a resident.
     *
     * @return false when the resident is missing, too far away, busy or the
     *         viewer has no right.
     */
    public static boolean open(ServerPlayer viewer, KingdomNpcData.Worker worker) {
        if (viewer == null || worker == null || worker.entityId == null
                || worker.employer == null
                || (!viewer.hasPermissions(2) && !viewer.getUUID().equals(worker.employer))
                || !viewer.level().dimension().location().toString().equals(worker.dimension))
            return false;
        var entity = viewer.serverLevel().getEntity(worker.entityId);
        if (!(entity instanceof Villager resident) || !resident.isAlive()) return false;
        if (viewer.distanceToSqr(resident) > 64) return false;
        Container personal = KingdomMcaAdapter.isResident(resident)
                ? KingdomResidentInventory.personal(resident) : null;
        if (personal == null && worker.fieldInventory.size() > 36) return false;
        if (OPEN.putIfAbsent(worker.entityId, viewer.getUUID()) != null) return false;
        try {
            BagSlots mirror = personal == null ? new BagSlots(viewer.getServer(), worker) : null;
            var opened = viewer.openMenu(new SimpleMenuProvider(
                    (id, inventory, player) -> new KingdomNpcBagMenu(id, inventory, viewer, worker, personal, mirror),
                    Component.literal("Инвентарь NPC: " + worker.name)));
            if (opened.isEmpty()) {
                OPEN.remove(worker.entityId, viewer.getUUID());
                return false;
            }
            return true;
        } catch (RuntimeException error) {
            OPEN.remove(worker.entityId, viewer.getUUID());
            throw error;
        }
    }

    private KingdomNpcBagMenu(int id, Inventory inventory, ServerPlayer viewer,
                              KingdomNpcData.Worker worker, Container personal, BagSlots mirror) {
        super(personal != null ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x4,
                id, inventory, personal != null ? personal : mirror,
                personal != null ? 3 : 4);
        this.viewer = viewer;
        this.npcId = worker.id;
        this.entityId = worker.entityId;
        this.bag = mirror;
    }

    public boolean isForNpc(int id) { return this.npcId == id; }

    @Override public boolean stillValid(Player player) {
        if (player != viewer) return false;
        var worker = KingdomNpcData.get(viewer.getServer()).workers.get(npcId);
        if (worker == null || !entityId.equals(worker.entityId)
                || worker.employer == null
                || (!viewer.hasPermissions(2) && !viewer.getUUID().equals(worker.employer))
                || !worker.dimension.equals(viewer.level().dimension().location().toString()))
            return false;
        var entity = viewer.serverLevel().getEntity(entityId);
        return entity != null && entity.isAlive() && viewer.distanceToSqr(entity) <= 64;
    }

    @Override public void removed(Player player) {
        if (bag != null) bag.flush();
        OPEN.remove(entityId, viewer.getUUID());
        super.removed(player);
    }

    /**
     * The mirror of the bag of the Kingdom for a resident without MCA.
     *
     * <p>The 36 slots are the saved list of the worker. The list is written
     * back when a slot changes and when the menu closes.
     */
    private static final class BagSlots extends SimpleContainer {
        private final net.minecraft.server.MinecraftServer server;
        private final KingdomNpcData.Worker worker;
        private boolean loading = true;

        BagSlots(net.minecraft.server.MinecraftServer server, KingdomNpcData.Worker worker) {
            super(36);
            this.server = server;
            this.worker = worker;
            for (int i = 0; i < worker.fieldInventory.size() && i < 36; i++)
                super.setItem(i, worker.fieldInventory.get(i).copy());
            loading = false;
        }

        @Override public void setChanged() {
            super.setChanged();
            flush();
        }

        void flush() {
            if (loading) return;
            worker.fieldInventory.clear();
            for (int i = 0; i < 36; i++) {
                var slot = super.getItem(i);
                if (!slot.isEmpty()) worker.fieldInventory.add(slot.copy());
            }
            KingdomNpcData.get(server).setDirty();
        }
    }
}
