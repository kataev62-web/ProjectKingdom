package ru.projectkingdom.npc;

/** Stock thresholds for NPC production and demand-board requests. No items or coins are created here. */
public final class EnterpriseStockPolicy {
    private EnterpriseStockPolicy() {}
    public record Thresholds(int minimum, int maximum) {
        public Thresholds {
            if (minimum < 0 || maximum < minimum) throw new IllegalArgumentException("stock thresholds");
        }
    }
    /** Hysteresis: start at/below minimum, keep running until maximum. */
    public static boolean nextProduction(Thresholds limits, int physicalStock, boolean wasRunning) {
        if (limits == null || physicalStock < 0) throw new IllegalArgumentException("stock");
        if (physicalStock >= limits.maximum()) return false;
        if (physicalStock <= limits.minimum()) return true;
        return wasRunning;
    }
    /** Maximum units allowed for a single real-resource-consuming production cycle. */
    public static int productionBatch(Thresholds limits, int physicalStock, boolean wasRunning, int recipeBatch) {
        if (recipeBatch <= 0) throw new IllegalArgumentException("recipeBatch");
        if (!nextProduction(limits, physicalStock, wasRunning)) return 0;
        return (int)Math.min((long)recipeBatch, (long)limits.maximum() - physicalStock);
    }
    /** Open demand only below minimum; subtract already committed incoming shipments. */
    public static int reorderQuantity(Thresholds limits, int physicalStock, int committedIncoming) {
        if (limits == null || physicalStock < 0 || committedIncoming < 0) throw new IllegalArgumentException("stock");
        long available = (long)physicalStock + committedIncoming;
        if (physicalStock >= limits.minimum() || available >= limits.maximum()) return 0;
        return (int)(limits.maximum() - available);
    }
}
