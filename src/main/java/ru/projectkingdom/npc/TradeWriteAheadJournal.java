package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import static java.nio.file.StandardOpenOption.*;

/** Durable fail-closed intent records. NOT an atomic commit for Minecraft chunk/SavedData writes.
 * Each record is immutable, checksummed and replaced via a same-directory atomic rename.
 * Callers MUST quarantine both parties while an intent exists; no automatic replay.
 */
public final class TradeWriteAheadJournal {
    private final Path directory;
    public TradeWriteAheadJournal(Path directory) { this.directory = Objects.requireNonNull(directory); }
    public record Entry(UUID id, UUID buyer, UUID seller, String snapshotDigest, String state) {}
    /** A comparison is diagnostic only: matching a digest is not proof that all chunks
     * and SavedData were durably flushed together. No automatic unlocking. */
    public enum RecoveryAssessment { BEFORE, AFTER, MIXED_OR_CHANGED, LEGACY_UNVERIFIABLE }
    public static RecoveryAssessment assess(Entry entry, String currentDigest) {
        if (entry == null || currentDigest == null) return RecoveryAssessment.MIXED_OR_CHANGED;
        String[] parts = entry.snapshotDigest().split(":", -1);
        if (parts.length != 2) return RecoveryAssessment.LEGACY_UNVERIFIABLE;
        if (currentDigest.equals(parts[0])) return RecoveryAssessment.BEFORE;
        if (currentDigest.equals(parts[1])) return RecoveryAssessment.AFTER;
        return RecoveryAssessment.MIXED_OR_CHANGED;
    }

    public synchronized void prepare(UUID id, UUID buyer, UUID seller, String snapshotDigest) throws IOException {
        if (id == null || buyer == null || seller == null || buyer.equals(seller)
                || snapshotDigest == null || !validDigest(snapshotDigest))
            throw new IllegalArgumentException("invalid trade intent");
        Files.createDirectories(directory);
        if (Files.exists(file(id))) throw new FileAlreadyExistsException(id.toString());
        write(new Entry(id, buyer, seller, snapshotDigest, "PREPARED"));
    }
    public synchronized void markForAudit(UUID id) throws IOException {
        Entry e = read(id);
        if (!"PREPARED".equals(e.state())) throw new IOException("unexpected journal state");
        write(new Entry(e.id(), e.buyer(), e.seller(), e.snapshotDigest(), "NEEDS_RECONCILIATION"));
    }
    /** Durable terminal receipt. Only after an operator has verified both inventories
     * and both cash balances in the persisted world. Never auto-close on sale success. */
    public synchronized void closeReconciled(UUID id) throws IOException {
        Entry e = read(id);
        if (!"NEEDS_RECONCILIATION".equals(e.state()))
            throw new IOException("trade is not awaiting reconciliation");
        write(new Entry(e.id(), e.buyer(), e.seller(), e.snapshotDigest(), "RECONCILED"));
    }
    public synchronized Entry read(UUID id) throws IOException {
        String raw = Files.readString(file(id));
        String[] lines = raw.split("\\n", -1);
        if (lines.length != 6 || !lines[0].equals("PKTRADE1")) throw new IOException("invalid journal format");
        String payload = String.join("\n", Arrays.copyOf(lines,5));
        if (!checksum(payload).equals(lines[5])) throw new IOException("journal checksum mismatch");
        try {
            Entry e = new Entry(UUID.fromString(lines[1]),UUID.fromString(lines[2]),
                    UUID.fromString(lines[3]),lines[4].split(":",2)[1],lines[4].split(":",2)[0]);
            if (!e.id().equals(id) || !validDigest(e.snapshotDigest())
                    || !(e.state().equals("PREPARED") || e.state().equals("NEEDS_RECONCILIATION") || e.state().equals("RECONCILED")))
                throw new IOException("invalid journal values");
            return e;
        } catch (RuntimeException ex) { throw new IOException("corrupt journal",ex); }
    }
    /** Includes terminal receipts: a journal receipt alone cannot prove Minecraft chunks were saved. */
    public synchronized List<Entry> allEntries() throws IOException {
        if (!Files.exists(directory)) return List.of();
        List<Entry> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory,"*.txn")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                UUID id;
                try { id = UUID.fromString(name.substring(0,name.length()-4)); }
                catch (RuntimeException ex) { throw new IOException("invalid journal filename",ex); }
                result.add(read(id));
            }
        }
        return result;
    }
    public synchronized List<Entry> unresolved() throws IOException {
        if (!Files.exists(directory)) return List.of();
        List<Entry> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory,"*.txn")) {
            for (Path path:stream) {
                UUID id;
                try { id=UUID.fromString(path.getFileName().toString().replaceFirst("\\.txn$","")); }
                catch (IllegalArgumentException ex) { throw new IOException("invalid journal filename",ex); }
                Entry entry = read(id);
                if (!"RECONCILED".equals(entry.state())) result.add(entry);
            }
        }
        return result;
    }
    private static boolean validDigest(String digest) {
        return digest.matches("[0-9a-f]{64}") || digest.matches("[0-9a-f]{64}:[0-9a-f]{64}");
    }
    private Path file(UUID id) { return directory.resolve(id+".txn"); }
    private void write(Entry e) throws IOException {
        String payload="PKTRADE1\n"+e.id()+"\n"+e.buyer()+"\n"+e.seller()+"\n"+e.state()+":"+e.snapshotDigest();
        byte[] bytes=(payload+"\n"+checksum(payload)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path temp=Files.createTempFile(directory,"trade-",".tmp");
        boolean moved=false;
        try {
            try(FileChannel ch=FileChannel.open(temp,WRITE,TRUNCATE_EXISTING)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())ch.write(buffer);
                ch.force(true);
            }
            Files.move(temp,file(e.id()),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            moved=true;
            // If the filesystem cannot durably sync directory metadata, fail closed.
            try(FileChannel dir=FileChannel.open(directory,READ)){dir.force(true);}
        } finally {if(!moved)Files.deleteIfExists(temp);}
    }
    private static String checksum(String value) {
        CRC32 crc=new CRC32();crc.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
}
