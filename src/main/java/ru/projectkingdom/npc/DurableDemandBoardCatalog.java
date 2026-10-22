package ru.projectkingdom.npc;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Crash-safe snapshot of board listings and acknowledged delivery IDs.
 * This is NOT a physical inventory/payment ledger. Never acknowledge before physical reconciliation.
 */
public final class DurableDemandBoardCatalog {
    private static final int MAGIC = 0x4b444231, VERSION = 1, MAX = 100_000;
    private final Path file;
    private final Map<UUID, EnterpriseDemandBoard.Listing> listings = new HashMap<>();
    private final Set<UUID> acknowledged = new HashSet<>();

    public DurableDemandBoardCatalog(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        if (Files.exists(this.file)) load();
    }
    public Path storagePath() { return file; }
    public synchronized int size() { return listings.size(); }
    public synchronized boolean acknowledged(UUID deliveryId) { return acknowledged.contains(deliveryId); }
    public synchronized List<EnterpriseDemandBoard.Listing> page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 50) throw new IllegalArgumentException("page");
        return listings.values().stream().sorted(Comparator.comparing(x -> x.listingId().toString()))
                .skip(offset).limit(limit).toList();
    }
    public synchronized void upsert(EnterpriseDemandBoard.Listing listing) throws IOException {
        Objects.requireNonNull(listing);
        Map<UUID, EnterpriseDemandBoard.Listing> next = new HashMap<>(listings);
        next.entrySet().removeIf(e -> e.getValue().key().equals(listing.key()));
        next.put(listing.listingId(), listing);
        persist(next, acknowledged);
        listings.clear(); listings.putAll(next);
    }
    public synchronized void remove(UUID listingId) throws IOException {
        Map<UUID, EnterpriseDemandBoard.Listing> next = new HashMap<>(listings);
        next.remove(Objects.requireNonNull(listingId));
        persist(next, acknowledged);
        listings.clear(); listings.putAll(next);
    }
    /** Call only after the physical trade coordinator has durably confirmed transfer. */
    public synchronized boolean acknowledgeCommitted(EnterpriseDemandBoard.Delivery delivery) throws IOException {
        Objects.requireNonNull(delivery);
        if (acknowledged.contains(delivery.deliveryId())) return false;
        EnterpriseDemandBoard.Listing old = listings.get(delivery.listingId());
        if (old == null) throw new IllegalArgumentException("unknown listing; reconcile manually");
        Set<UUID> ids = new HashSet<>(acknowledged);
        EnterpriseDemandBoard.Listing remaining = EnterpriseDemandBoard.committed(old, delivery, ids);
        Map<UUID, EnterpriseDemandBoard.Listing> next = new HashMap<>(listings);
        if (remaining == null) next.remove(old.listingId()); else next.put(old.listingId(), remaining);
        persist(next, ids);
        listings.clear(); listings.putAll(next);
        acknowledged.clear(); acknowledged.addAll(ids);
        return true;
    }
    private void persist(Map<UUID, EnterpriseDemandBoard.Listing> entries, Set<UUID> ids) throws IOException {
        if (entries.size() > MAX || ids.size() > MAX) throw new IOException("board limit");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(entries.size());
            for (var listing : entries.values()) {
                out.writeUTF(listing.listingId().toString());
                out.writeUTF(listing.key().enterprise().toString());
                out.writeUTF(listing.key().item());
                out.writeInt(listing.remaining()); out.writeLong(listing.maximumUnitCopper());
            }
            out.writeInt(ids.size());
            for (UUID id : ids) out.writeUTF(id.toString());
        }
        byte[] payload = bytes.toByteArray();
        CRC32 crc = new CRC32(); crc.update(payload);
        Path parent = file.getParent(); Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "demand-board-", ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile()); DataOutputStream out = new DataOutputStream(fos)) {
                out.writeInt(payload.length); out.write(payload); out.writeLong(crc.getValue());
                out.flush(); fos.getFD().sync();
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }
    private void load() throws IOException {
        byte[] payload;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int length = in.readInt();
            if (length < 12 || length > 32_000_000) throw new IOException("invalid snapshot size");
            payload = in.readNBytes(length);
            if (payload.length != length) throw new EOFException("truncated snapshot");
            long checksum = in.readLong();
            if (in.read() != -1) throw new IOException("trailing snapshot bytes");
            CRC32 crc = new CRC32(); crc.update(payload);
            if (checksum != crc.getValue()) throw new IOException("snapshot checksum mismatch");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("unknown snapshot format");
            int count = in.readInt(); if (count < 0 || count > MAX) throw new IOException("invalid listing count");
            Set<EnterpriseDemandBoard.Key> keys = new HashSet<>();
            for (int i=0; i<count; i++) {
                UUID id = UUID.fromString(in.readUTF()); UUID owner = UUID.fromString(in.readUTF());
                String item = in.readUTF(); int quantity = in.readInt(); long price = in.readLong();
                var listing = new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(owner,item),id,quantity,price);
                if (listings.putIfAbsent(id,listing) != null || !keys.add(listing.key())) throw new IOException("duplicate listing");
            }
            int completed = in.readInt(); if (completed < 0 || completed > MAX) throw new IOException("invalid delivery count");
            for (int i=0; i<completed; i++) if (!acknowledged.add(UUID.fromString(in.readUTF()))) throw new IOException("duplicate delivery");
            if (in.available() != 0) throw new IOException("unexpected snapshot data");
        } catch (IllegalArgumentException ex) { throw new IOException("invalid snapshot content",ex); }
    }
}
