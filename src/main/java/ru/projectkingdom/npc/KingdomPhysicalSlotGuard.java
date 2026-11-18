package ru.projectkingdom.npc;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Physical slot provenance for same-tick money movement.
 * Keys compare the actual backing OBJECT by identity (not equals/hashCode).
 * Unknown slots, duplicates inside either wrapper, and any cross-wrapper
 * overlap must fail closed before taking or inserting a single coin.
 */
public final class KingdomPhysicalSlotGuard {
    private KingdomPhysicalSlotGuard() {}

    public record Slot(Object backing, int index) {
        public Slot {
            if (backing == null || index < 0)
                throw new IllegalArgumentException("Missing physical backing/slot");
        }
    }

    public static boolean disjoint(List<Slot> source, List<Slot> target) {
        if (source == null || target == null || source.isEmpty() || target.isEmpty())
            return false;
        IdentityHashMap<Object, BitSet> used = new IdentityHashMap<>();
        if (!register(source, used)) return false;
        return register(target, used);
    }

    private static boolean register(List<Slot> slots, IdentityHashMap<Object, BitSet> used) {
        for (Slot slot : slots) {
            if (slot == null || slot.backing() == null || slot.index() < 0) return false;
            BitSet taken = used.computeIfAbsent(slot.backing(), ignored -> new BitSet());
            if (taken.get(slot.index())) return false;
            taken.set(slot.index());
        }
        return true;
    }
}
