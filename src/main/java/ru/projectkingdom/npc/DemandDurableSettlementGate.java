package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** Fail-closed durable delivery-ID gate for the in-memory physical settlement reference.
 * A PREPARED entry is NEVER automatically retried after an interruption: Minecraft
 * inventories may already have changed. An operator must reconcile the four containers.
 * This is not a replacement for a Forge ItemStack transaction / persistent inventory journal.
 */
public final class DemandDurableSettlementGate {
    public enum Status { COMMITTED, REJECTED, DUPLICATE, NEEDS_REVIEW }
    public record Decision(Status status, DemandPhysicalSettlementEngine.Result reason) {}
    private static final Object PROCESS_SETTLEMENT_LOCK = new Object();
    private final Path directory;
    public DemandDurableSettlementGate(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
    }
    private Path marker(UUID id) { return directory.resolve(id.toString()+".txn"); }
    private static void durableWrite(Path file, String content, boolean createNew) throws IOException {
        byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileChannel channel=FileChannel.open(file, WRITE, createNew ? CREATE_NEW : CREATE, createNew ? DSYNC : TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(bytes)); channel.force(true);
        }
    }
    private static String read(Path p) throws IOException { return Files.readString(p); }
    /** Serializes all local exchanges sharing this gate instance. Cross-process coordination
     * requires an exclusive server instance and filesystem locking, not provided here. */
    public Decision settle(UUID id, String item, int quantity, long unitCopper,
            DemandPhysicalSettlementEngine.Inventory supplier,
            DemandPhysicalSettlementEngine.Inventory warehouse,
            DemandPhysicalSettlementEngine.Inventory cashbox,
            DemandPhysicalSettlementEngine.Inventory wallet) throws IOException {
        synchronized (PROCESS_SETTLEMENT_LOCK) {
        Objects.requireNonNull(id);
        Path p=marker(id);
        if (Files.exists(p)) {
            String state=read(p);
            return new Decision(state.startsWith("COMMITTED\n") ? Status.DUPLICATE : Status.NEEDS_REVIEW, null);
        }
        // A different pending transaction may already have mutated these containers.
        // Fail closed until an operator reconciles it. Corrupt markers also quarantine.
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(directory,"*.txn")) {
            for (Path entry:entries) {
                if (entry.equals(p)) continue;
                String state=read(entry);
                if (!state.startsWith("COMMITTED\n") && !state.startsWith("REJECTED\n"))
                    return new Decision(Status.NEEDS_REVIEW,null);
            }
        }
        // A durable PREPARED marker is written before touching any inventory.
        // The reference engine may reject the transaction: rejection is recorded, not retried.
        durableWrite(p,"PREPARED\n"+id+"\n",true);
        try {
            var result=DemandPhysicalSettlementEngine.settle(id,item,quantity,unitCopper,
                    supplier,warehouse,cashbox,wallet,new HashSet<>());
            if (result.result()==DemandPhysicalSettlementEngine.Result.COMMITTED) {
                durableWrite(p,"COMMITTED\n"+id+"\n",false);
                return new Decision(Status.COMMITTED,result.result());
            }
            durableWrite(p,"REJECTED\n"+id+"\n"+result.result()+"\n",false);
            return new Decision(Status.REJECTED,result.result());
        } catch (RuntimeException | IOException ex) {
            // PREPARED remains; never auto-repeat a potentially partial exchange.
            throw ex;
        }
    }
    }
    public synchronized Status inspect(UUID id) throws IOException {
        Path p=marker(id);
        if (!Files.exists(p)) return null;
        String state=read(p);
        if (state.startsWith("COMMITTED\n")) return Status.COMMITTED;
        if (state.startsWith("REJECTED\n")) return Status.REJECTED;
        return Status.NEEDS_REVIEW;
    }
}
