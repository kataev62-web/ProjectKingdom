package ru.projectkingdom.npc;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persisted, information-only buy/sell notices. No tick loop, chunks or inventory access. */
public final class DurableSimpleTradeNoticeBoard {
    private static final int MAGIC = 0x4b4e4231;
    private final Path file;
    private final SimpleTradeNoticeBoard board = new SimpleTradeNoticeBoard();
    private final Map<UUID, SimpleTradeNoticeBoard.Notice> records = new LinkedHashMap<>();

    public DurableSimpleTradeNoticeBoard(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        if (Files.exists(this.file)) load();
    }

    public synchronized void post(SimpleTradeNoticeBoard.Notice notice) throws IOException {
        Objects.requireNonNull(notice);
        var previous = records.get(notice.id());
        if (previous != null && !previous.owner().equals(notice.owner()))
            throw new IllegalArgumentException("Cannot change notice owner");
        Map<UUID, SimpleTradeNoticeBoard.Notice> next = new LinkedHashMap<>(records);
        next.put(notice.id(), notice);
        save(next);
        records.clear(); records.putAll(next); board.post(notice);
    }

    public synchronized boolean remove(UUID id) throws IOException {
        if (!records.containsKey(id)) return false;
        Map<UUID, SimpleTradeNoticeBoard.Notice> next = new LinkedHashMap<>(records);
        next.remove(id);
        save(next);
        records.remove(id); board.remove(id);
        return true;
    }

    /** Called ONLY after physical trade confirmation. */
    public synchronized boolean confirmedTrade(UUID id, String itemId, int amount) throws IOException {
        var current = records.get(id);
        if (current == null || !current.itemId().equals(itemId) || amount <= 0 || amount > current.quantity()) return false;
        Map<UUID, SimpleTradeNoticeBoard.Notice> next = new LinkedHashMap<>(records);
        if (amount == current.quantity()) next.remove(id);
        else next.put(id, new SimpleTradeNoticeBoard.Notice(current.id(), current.owner(), current.side(),
                current.itemId(), current.quantity() - amount, current.priceCopper(), current.location()));
        save(next);
        if (amount == current.quantity()) { records.remove(id); board.remove(id); }
        else { var updated = next.get(id); records.put(id, updated); board.post(updated); }
        return true;
    }

    /** Read one notice for a local shop menu; never executes a trade. */
    public synchronized SimpleTradeNoticeBoard.Notice notice(UUID id) {
        return records.get(id);
    }

    public synchronized List<SimpleTradeNoticeBoard.Notice> page(SimpleTradeNoticeBoard.Side side, int offset, int limit) {
        return board.page(side, offset, limit);
    }

    /** Find the advertised purchase terms for a specific enterprise and item.
     * Read-only, event-driven: no inventory scanning, chunk loading or tick loop.
     * Multiple notices for one item are returned separately, never merged silently.
     */
    public synchronized List<SimpleTradeNoticeBoard.Notice> purchases(UUID enterprise, String itemId) {
        Objects.requireNonNull(enterprise);
        Objects.requireNonNull(itemId);
        return records.values().stream()
                .filter(n -> n.side() == SimpleTradeNoticeBoard.Side.BUY
                        && n.owner().equals(enterprise) && n.itemId().equals(itemId))
                .sorted(Comparator.comparing(n -> n.id().toString())).toList();
    }

    /** Read-only offers for a specific shop and direction; called when a player opens the shop. */
    public synchronized List<SimpleTradeNoticeBoard.Notice> offers(UUID shop, SimpleTradeNoticeBoard.Side side) {
        Objects.requireNonNull(shop); Objects.requireNonNull(side);
        return records.values().stream()
                .filter(n -> n.owner().equals(shop) && n.side() == side)
                .sorted(Comparator.comparing(n -> n.id().toString())).toList();
    }

    private void load() throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid notice board file");
            int count = in.readInt();
            if (count < 0 || count > 100_000) throw new IOException("Invalid notice count");
            for (int i = 0; i < count; i++) {
                UUID id = UUID.fromString(in.readUTF());
                UUID owner = UUID.fromString(in.readUTF());
                SimpleTradeNoticeBoard.Side side = SimpleTradeNoticeBoard.Side.valueOf(in.readUTF());
                var n = new SimpleTradeNoticeBoard.Notice(id, owner, side, in.readUTF(), in.readInt(), in.readLong(), in.readUTF());
                if (records.putIfAbsent(id, n) != null) throw new IOException("Duplicate notice ID");
                board.post(n);
            }
            if (in.read() != -1) throw new IOException("Unexpected notice board trailing data");
        } catch (RuntimeException ex) { throw new IOException("Corrupt notice board", ex); }
    }

    private void save(Map<UUID, SimpleTradeNoticeBoard.Notice> next) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "notice-board-", ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(temp.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos))) {
                out.writeInt(MAGIC); out.writeInt(next.size());
                for (var n : next.values()) {
                    out.writeUTF(n.id().toString()); out.writeUTF(n.owner().toString());
                    out.writeUTF(n.side().name()); out.writeUTF(n.itemId());
                    out.writeInt(n.quantity()); out.writeLong(n.priceCopper()); out.writeUTF(n.location());
                }
                out.flush(); fos.getFD().sync();
            }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
