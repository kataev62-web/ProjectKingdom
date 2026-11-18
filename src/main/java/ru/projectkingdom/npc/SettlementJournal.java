package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.zip.CRC32;

/**
 * Append-only durable record log for the settlement authority.
 *
 * <p>Every record is one line {@code KIND|payload|crc32}. The checksum covers
 * {@code KIND|payload}, so a line with a broken checksum is never read as a
 * decision. A record is forced to disk before the call returns, so a committed
 * decision survives a process crash. A power loss may still leave a torn tail:
 * the loader stops at the first damaged record and reports it, and the caller
 * has to treat everything after that point as undecided.
 *
 * <p>The log records intent and progress only. It is not an atomic commit for
 * Minecraft chunk or SavedData writes, and it never moves items or money itself.
 */
public final class SettlementJournal {
    public enum Kind { PREPARED, COMMITTED, RECEIPT, REJECTED, QUARANTINED }

    /** One durable record. */
    public record Record(Kind kind, String payload) {}

    /** The valid prefix of the log plus the verdict about the tail. */
    public record LoadResult(List<Record> records, boolean damaged, String problem) {}

    private final Path file;
    private long durableBytes;

    public SettlementJournal(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        this.durableBytes = size();
    }

    public Path file() { return file; }

    /** Bytes known to be on disk. */
    public synchronized long durableLength() { return durableBytes; }

    /**
     * Appends one record and forces it to disk.
     *
     * @param hook optional crash-injection callback. It receives the boundary name
     *             and the durable length known at that moment.
     */
    public synchronized void append(Kind kind, String payload, BiConsumer<String, Long> hook) throws IOException {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(payload);
        if (payload.indexOf('\n') >= 0 || payload.indexOf('\r') >= 0)
            throw new IllegalArgumentException("payload must be one line");
        String body = kind.name() + "|" + payload;
        String line = body + "|" + checksum(body) + "\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (hook != null) hook.accept("journal.before_write", durableBytes);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            if (hook != null) hook.accept("journal.after_write", durableBytes);
            channel.force(true);
            durableBytes += bytes.length;
            if (hook != null) hook.accept("journal.after_force", durableBytes);
        }
    }

    /** Reads the valid prefix of the log. A damaged tail is reported, never ignored. */
    public synchronized LoadResult load() throws IOException {
        List<Record> records = new ArrayList<>();
        if (!Files.exists(file)) return new LoadResult(records, false, null);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isEmpty()) continue;
            int separator = line.lastIndexOf('|');
            if (separator <= 0) return damaged(records, index, "no checksum");
            String body = line.substring(0, separator);
            if (!checksum(body).equals(line.substring(separator + 1)))
                return damaged(records, index, "checksum mismatch");
            int bar = body.indexOf('|');
            if (bar <= 0) return damaged(records, index, "no kind");
            Kind kind;
            try { kind = Kind.valueOf(body.substring(0, bar)); }
            catch (RuntimeException ex) { return damaged(records, index, "unknown kind"); }
            records.add(new Record(kind, body.substring(bar + 1)));
        }
        return new LoadResult(records, false, null);
    }

    private static LoadResult damaged(List<Record> records, int index, String reason) {
        return new LoadResult(List.copyOf(records), true, "record " + (index + 1) + ": " + reason);
    }

    private long size() {
        try { return Files.exists(file) ? Files.size(file) : 0L; }
        catch (IOException ex) { return 0L; }
    }

    private static String checksum(String value) {
        CRC32 crc = new CRC32();
        crc.update(value.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
}
