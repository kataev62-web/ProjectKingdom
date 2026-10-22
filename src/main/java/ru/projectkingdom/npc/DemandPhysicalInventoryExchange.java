package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Four real inventory endpoints; never creates goods or copper. Caller must hold
 * exclusive access to all four endpoints, including automation, for the entire call.
 * A partially failed write MUST be quarantined by the durable delivery coordinator.
 */
public final class DemandPhysicalInventoryExchange implements DemandDeliveryCoordinator.PhysicalExchange {
    public interface Ports {
        /** Actual copper in the enterprise cashbox; never a virtual balance. */
        long cashboxCopper(UUID enterprise) throws IOException;
        /** Must read actual inventories, not accounting balances. */
        DemandPhysicalTransferPlan.Snapshot snapshot(EnterpriseDemandBoard.Listing listing,
                                                     EnterpriseDemandBoard.Delivery delivery) throws IOException;
        /** Move exactly quantity of the listed item from supplier to enterprise. */
        void moveGoods(EnterpriseDemandBoard.Listing listing, int quantity) throws IOException;
        /** Move exactly copper existing coins from enterprise cashbox to supplier wallet. */
        void moveCopper(UUID enterprise, long copper) throws IOException;
    }
    private final Ports ports;
    private EnterpriseDemandBoard.Delivery active;
    private DemandPhysicalTransferPlan.Snapshot before;
    private DemandPhysicalTransferPlan.Decision plan;
    public DemandPhysicalInventoryExchange(Ports ports) { this.ports = Objects.requireNonNull(ports); }
    @Override public long availableCopper(UUID enterprise) throws IOException {
        // Preliminary check only; the physical amount is re-read before mutation.
        return ports.cashboxCopper(enterprise);
    }
    @Override public synchronized boolean exchange(EnterpriseDemandBoard.Listing listing,
                                                     EnterpriseDemandBoard.Delivery delivery) throws IOException {
        if (active != null) return false; // one-shot adapter; never replay a partial transfer
        active = delivery;
        before = ports.snapshot(listing, delivery);
        plan = DemandPhysicalTransferPlan.inspect(listing, delivery, before);
        if (!plan.ready()) return false;
        ports.moveGoods(listing, plan.goodsToMove());
        ports.moveCopper(listing.key().enterprise(), plan.copperToMove());
        return true;
    }
    @Override public synchronized boolean verified(EnterpriseDemandBoard.Listing listing,
                                                    EnterpriseDemandBoard.Delivery delivery) throws IOException {
        if (active == null || !active.equals(delivery) || plan == null || !plan.ready()) return false;
        var after = ports.snapshot(listing, delivery);
        long goods = plan.goodsToMove(), copper = plan.copperToMove();
        // Verify BOTH endpoints of EACH transfer. Ports snapshot fields represent
        // supplier goods, buyer available capacity, buyer copper, seller coin capacity.
        try {
            return after.offeredItem().equals(before.offeredItem())
                && after.supplierGoods() == Math.subtractExact(before.supplierGoods(), goods)
                && after.enterpriseGoodsSpace() == Math.subtractExact(before.enterpriseGoodsSpace(), goods)
                && after.enterpriseCopper() == Math.subtractExact(before.enterpriseCopper(), copper)
                && after.supplierCopperSpace() == Math.subtractExact(before.supplierCopperSpace(), copper);
        } catch (ArithmeticException ex) { return false; }
    }
}
