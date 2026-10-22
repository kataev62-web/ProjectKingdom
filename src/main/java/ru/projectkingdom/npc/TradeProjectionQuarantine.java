package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;

/** Durable fail-closed marker for a physical two-container projection.
 * Never auto-retry an interrupted projection; an operator must reconcile it.
 */
public final class TradeProjectionQuarantine {
    private final Path directory;
    public TradeProjectionQuarantine(Path directory) { this.directory = directory; }
    private Path marker(UUID tx) { return directory.resolve(tx.toString() + ".pending"); }
    public boolean isQuarantined(UUID tx) { return tx == null || Files.exists(marker(tx)); }
    /** A marker is synced before the first physical mutation. Existing markers reject replay. */
    public boolean begin(UUID tx, UUID buyer, UUID seller) throws IOException {
        if (tx == null || buyer == null || seller == null || buyer.equals(seller))
            throw new IllegalArgumentException("invalid transaction participants");
        Files.createDirectories(directory);
        byte[] bytes = (tx + "\n" + buyer + "\n" + seller + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (FileChannel channel = FileChannel.open(marker(tx), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes)); channel.force(true);
            return true;
        } catch (FileAlreadyExistsException alreadyPending) { return false; }
    }
    /** Only call after BOTH physical accounts are independently verified at AFTER state.
     * A crash during marker deletion remains safe (may require manual review).
     */
    public void verifiedComplete(UUID tx) throws IOException {
        if (tx == null) throw new IllegalArgumentException("tx");
        Files.deleteIfExists(marker(tx));
    }
}
