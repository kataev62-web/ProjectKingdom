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
    /** Consistent source for production, purchasing and in-flight reservations. */
    public record StockState(int physical,int incoming,int inProduction,boolean complete){
        public StockState{
            if(physical<0||incoming<0||inProduction<0)throw new IllegalArgumentException("stock state");
        }
        public int committedTotal(){return (int)Math.min(Integer.MAX_VALUE,(long)physical+incoming+inProduction);}
    }
    public static boolean nextProduction(Thresholds p,StockState state,boolean running){
        return state!=null&&state.complete()&&nextProduction(p,state.committedTotal(),running);
    }
    public static int productionBatch(Thresholds p,StockState state,boolean running,int batch){
        return state!=null&&state.complete()?productionBatch(p,state.committedTotal(),running,batch):0;
    }
    public static int reorderQuantity(Thresholds p,StockState state){
        if(state==null||!state.complete())return 0;
        return reorderQuantity(p,(int)Math.min(Integer.MAX_VALUE,(long)state.physical()+state.inProduction()),state.incoming());
    }
    /** Open demand at/below minimum; subtract already committed incoming shipments. */
    public static int reorderQuantity(Thresholds limits, int physicalStock, int committedIncoming) {
        if (limits == null || physicalStock < 0 || committedIncoming < 0) throw new IllegalArgumentException("stock");
        long available = (long)physicalStock + committedIncoming;
        if (physicalStock > limits.minimum() || limits.minimum() == 0 || available >= limits.maximum()) return 0;
        return (int)(limits.maximum() - available);
    }
}
