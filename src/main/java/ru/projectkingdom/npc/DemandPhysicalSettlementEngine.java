package ru.projectkingdom.npc;

import java.util.*;

/** In-memory, slot-backed reference settlement for a single server-thread transaction.
 * No coins/items are minted. A live Forge adapter must provide transactional inventory
 * access and durable recovery before this is exposed to players.
 */
public final class DemandPhysicalSettlementEngine {
    private DemandPhysicalSettlementEngine() {}
    public record Stack(String item, int count) {
        public Stack { Objects.requireNonNull(item); if (count < 0) throw new IllegalArgumentException("negative stack"); }
    }
    public static final class Inventory {
        private final int maxStack;
        private final ArrayList<Stack> slots;
        public Inventory(int slotCount, int maxStack) {
            if (slotCount <= 0 || maxStack <= 0) throw new IllegalArgumentException();
            this.maxStack = maxStack;
            this.slots = new ArrayList<>(Collections.nCopies(slotCount, new Stack("", 0)));
        }
        public synchronized void set(int slot, String item, int count) {
            if (count < 0 || count > maxStack || (count > 0 && item.isBlank())) throw new IllegalArgumentException();
            slots.set(slot, count == 0 ? new Stack("", 0) : new Stack(item, count));
        }
        public synchronized long count(String item) {
            return slots.stream().filter(s -> s.item.equals(item)).mapToLong(Stack::count).sum();
        }
        public synchronized List<Stack> snapshot() { return List.copyOf(slots); }
        private void replace(List<Stack> replacement) { slots.clear(); slots.addAll(replacement); }
    }
    public enum Result { COMMITTED, DUPLICATE, INVALID, INSUFFICIENT_GOODS, NO_GOODS_SPACE, INSUFFICIENT_COINS, NO_COIN_SPACE }
    public record Receipt(UUID deliveryId, String item, int quantity, long copper,
                          List<Stack> supplierBefore, List<Stack> warehouseBefore,
                          List<Stack> cashboxBefore, List<Stack> walletBefore) {}
    public record Outcome(Result result, Receipt receipt) {}
    public static final String COPPER = "kingdom:copper_coin";
    public static final String SILVER = "kingdom:silver_coin";
    public static final String GOLD = "kingdom:gold_coin";
    private static long value(String item) {
        return switch (item) { case COPPER -> 1; case SILVER -> 100; case GOLD -> 10000; default -> 0; };
    }
    private static long total(List<Stack> slots) {
        long result = 0;
        for (Stack s : slots) if (value(s.item) != 0) result = Math.addExact(result, Math.multiplyExact(s.count, value(s.item)));
        return result;
    }
    private static long remove(List<Stack> slots, String item, long quantity) {
        long remaining = quantity;
        for (int i=0; i<slots.size() && remaining>0; i++) {
            Stack s = slots.get(i);
            if (!s.item.equals(item)) continue;
            int take = (int)Math.min(remaining, s.count);
            slots.set(i, s.count == take ? new Stack("",0) : new Stack(item,s.count-take));
            remaining -= take;
        }
        return remaining;
    }
    private static long insert(List<Stack> slots, String item, long quantity, int maxStack) {
        long remaining = quantity;
        for (int pass=0; pass<2 && remaining>0; pass++) {
            for (int i=0; i<slots.size() && remaining>0; i++) {
                Stack s=slots.get(i);
                if (pass==0 ? !s.item.equals(item) : s.count!=0) continue;
                int add=(int)Math.min(remaining, maxStack-s.count);
                if (add>0) { slots.set(i,new Stack(item,s.count+add)); remaining-=add; }
            }
        }
        return remaining;
    }
    /** Exact physical denominations only: no implicit change-making or conversion. */
    public static synchronized Outcome settle(UUID id, String item, int quantity, long unitCopper,
            Inventory supplier, Inventory warehouse, Inventory cashbox, Inventory wallet, Set<UUID> completed) {
        Objects.requireNonNull(id); Objects.requireNonNull(item);
        Objects.requireNonNull(supplier); Objects.requireNonNull(warehouse);
        Objects.requireNonNull(cashbox); Objects.requireNonNull(wallet); Objects.requireNonNull(completed);
        if (supplier == warehouse || supplier == cashbox || supplier == wallet || warehouse == cashbox || warehouse == wallet || cashbox == wallet)
            return new Outcome(Result.INVALID,null);
        if (completed.contains(id)) return new Outcome(Result.DUPLICATE,null);
        if (quantity<=0 || unitCopper<=0 || value(item)!=0) return new Outcome(Result.INVALID,null);
        long price;
        try { price=Math.multiplyExact((long)quantity,unitCopper); }
        catch (ArithmeticException ex) { return new Outcome(Result.INVALID,null); }
        List<Stack> sb=supplier.snapshot(), wb=warehouse.snapshot(), cb=cashbox.snapshot(), vb=wallet.snapshot();
        var s=new ArrayList<>(sb); var w=new ArrayList<>(wb); var c=new ArrayList<>(cb); var v=new ArrayList<>(vb);
        if (remove(s,item,quantity)!=0) return new Outcome(Result.INSUFFICIENT_GOODS,null);
        if (insert(w,item,quantity,warehouse.maxStack)!=0) return new Outcome(Result.NO_GOODS_SPACE,null);
        if (total(c)<price) return new Outcome(Result.INSUFFICIENT_COINS,null);
        // Find exact coin combination from the *actual* cashbox. No virtual change.
        long[] available={count(c,GOLD),count(c,SILVER),count(c,COPPER)};
        long[] values={10000,100,1};
        long[] selected=exactSelection(available,values,price);
        if (selected==null) return new Outcome(Result.INSUFFICIENT_COINS,null);
        String[] coins={GOLD,SILVER,COPPER};
        for (int i=0;i<3;i++) {
            if (remove(c,coins[i],selected[i])!=0) throw new IllegalStateException("selection invalid");
            if (insert(v,coins[i],selected[i],wallet.maxStack)!=0) return new Outcome(Result.NO_COIN_SPACE,null);
        }
        if (total(cb)-total(c)!=price || total(v)-total(vb)!=price)
            throw new IllegalStateException("conservation failed");
        // Reference implementation: one atomic commit under the monitor. Not crash-atomic
        // against Minecraft block entities; live integration must use a durable journal.
        supplier.replace(s); warehouse.replace(w); cashbox.replace(c); wallet.replace(v);
        completed.add(id);
        return new Outcome(Result.COMMITTED,new Receipt(id,item,quantity,price,sb,wb,cb,vb));
    }
    private static long count(List<Stack> slots,String item) {
        return slots.stream().filter(s->s.item.equals(item)).mapToLong(Stack::count).sum();
    }
    private static long[] exactSelection(long[] have,long[] values,long target) {
        // Coins have divisibility 100:1; prefer large coins, but avoid greedy failure
        // when exact payment requires silver/copper instead of an unavailable gold.
        long maxGold=Math.min(have[0],target/values[0]);
        for (long g=maxGold;g>=0;g--) {
            long remainder=target-g*values[0];
            long silver=Math.min(have[1],remainder/values[1]);
            long copper=remainder-silver*values[1];
            if (copper<=have[2]) return new long[]{g,silver,copper};
            // If insufficient copper with maximal silver, fewer silver only increases copper.
            if (g==0) break;
        }
        return null;
    }
}
