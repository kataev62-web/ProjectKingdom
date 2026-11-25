package ru.projectkingdom.npc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Durable evidence, not a cross-file transaction. An existing journal blocks a new
 * server session until an operator reconciles the recorded inventories. Neither a
 * successful receipt nor a rollback proves Minecraft has saved all three owners.
 */
final class ResidentPurchaseJournal {
    private static final UUID FORMAT = UUID.nameUUIDFromBytes("kingdom.resident.purchase.v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final TradeAtomicStore store;
    private final ListTag entries = new ListTag();
    private long generation;
    private boolean blocked;
    ResidentPurchaseJournal(Path directory) {
        store = new TradeAtomicStore(directory, FORMAT);
        try { blocked = store.read().isPresent(); }
        catch (IOException | RuntimeException invalid) { blocked = true; }
    }
    boolean blocked() { return blocked; }
    boolean prepare(CompoundTag evidence) {
        if (blocked) return false;
        if (evidence.hasUUID("Operation"))
            for (int i=0; i<entries.size(); i++)
                if (entries.getCompound(i).hasUUID("Operation") && entries.getCompound(i).getUUID("Operation")
                        .equals(evidence.getUUID("Operation"))) return false;
        CompoundTag entry = evidence.copy(); entry.putString("State", "PREPARED");
        entries.add(entry);
        return flush();
    }
    void finish(boolean committed) {
        if (blocked || entries.isEmpty()) return;
        entries.getCompound(entries.size()-1).putString("State", committed ? "APPLIED" : "ROLLED_BACK");
        flush();
    }
    void quarantine() { blocked = true; }
    private boolean flush() {
        try {
            CompoundTag root = new CompoundTag(); root.put("Operations", entries);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bytes);
            generation = store.commit(generation, bytes.toByteArray());
            return true;
        } catch (IOException | RuntimeException failure) {
            blocked = true;
            return false;
        }
    }
}
