package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import static java.nio.file.StandardOpenOption.*;

/** A durable checkpoint *receipt*, not proof of Minecraft chunk persistence.
 * Only an external coordinator which has confirmed durable saves may issue it.
 * An unverified receipt must never be used to release quarantine.
 */
public final class TradeCheckpointLedger {
    private final Path directory;
    public TradeCheckpointLedger(Path directory) { this.directory = Objects.requireNonNull(directory); }
    public record Receipt(UUID tradeId, String afterDigest, String worldGeneration, long saveEpoch) {}

    public synchronized void record(Receipt receipt) throws IOException {
        Objects.requireNonNull(receipt);
        if (receipt.tradeId()==null || receipt.afterDigest()==null ||
            !receipt.afterDigest().matches("[0-9a-f]{64}") ||
            receipt.worldGeneration()==null || !receipt.worldGeneration().matches("[a-zA-Z0-9_-]{1,80}") ||
            receipt.saveEpoch()<1) throw new IllegalArgumentException("invalid checkpoint");
        Files.createDirectories(directory);
        Path target=path(receipt.tradeId());
        if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
        String payload="PKCHECK1\n"+receipt.tradeId()+"\n"+receipt.afterDigest()+"\n"+
            receipt.worldGeneration()+"\n"+receipt.saveEpoch();
        CRC32 crc=new CRC32(); crc.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] bytes=(payload+"\n"+Long.toHexString(crc.getValue())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path tmp=Files.createTempFile(directory,"checkpoint-",".tmp");
        boolean moved=false;
        try {
            try(FileChannel ch=FileChannel.open(tmp,WRITE,TRUNCATE_EXISTING)) {
                ByteBuffer b=ByteBuffer.wrap(bytes); while(b.hasRemaining()) ch.write(b); ch.force(true);
            }
            // Atomic rename without REPLACE_EXISTING is not guaranteed to reject
            // an existing destination on every filesystem. Hard-link publication
            // is atomic and CREATE-only: two ledger instances cannot overwrite a
            // receipt even if both observed the target as absent.
            Files.createLink(target, tmp);
            moved=true;
            try(FileChannel dir=FileChannel.open(directory,READ)) { dir.force(true); }
        } finally { Files.deleteIfExists(tmp); }
    }

    public synchronized Optional<Receipt> read(UUID tradeId) throws IOException {
        Path file=path(tradeId);
        if (!Files.exists(file)) return Optional.empty();
        String[] lines=Files.readString(file).split("\n",-1);
        if(lines.length!=6 || !"PKCHECK1".equals(lines[0])) throw new IOException("invalid checkpoint format");
        String payload=String.join("\n",Arrays.copyOf(lines,5));
        CRC32 crc=new CRC32(); crc.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!Long.toHexString(crc.getValue()).equals(lines[5])) throw new IOException("checkpoint checksum mismatch");
        try {
            Receipt r=new Receipt(UUID.fromString(lines[1]),lines[2],lines[3],Long.parseLong(lines[4]));
            if(!tradeId.equals(r.tradeId()) || !r.afterDigest().matches("[0-9a-f]{64}") ||
                !r.worldGeneration().matches("[a-zA-Z0-9_-]{1,80}") || r.saveEpoch()<1)
                throw new IOException("invalid checkpoint values");
            return Optional.of(r);
        } catch(RuntimeException e) { throw new IOException("corrupt checkpoint",e); }
    }

    /** Both world generation and digest must match; epoch cannot go backwards. */
    public boolean matches(UUID tradeId, String actualDigest, String worldGeneration, long durableEpoch) throws IOException {
        Optional<Receipt> receipt=read(tradeId);
        return receipt.isPresent() && receipt.get().afterDigest().equals(actualDigest) &&
            receipt.get().worldGeneration().equals(worldGeneration) && durableEpoch>=receipt.get().saveEpoch();
    }
    private Path path(UUID id) {return directory.resolve(Objects.requireNonNull(id)+".checkpoint");}
}
