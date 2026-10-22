package ru.projectkingdom.npc;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** Atomic single-file snapshot primitive for a future unified economy state.
 * IMPORTANT: it does NOT make Minecraft SavedData, chunk inventories, or an
 * external trade journal atomic. Never issue checkpoint receipts on its own.
 */
public final class TradeAtomicStore {
    private static final int MAGIC=0x504B4153; // PKAS
    private static final int VERSION=1;
    private static final int MAX_BYTES=32*1024*1024;
    private static final java.util.concurrent.ConcurrentHashMap<Path,Object> JVM_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private final Path directory;
    private final UUID world;
    public record Snapshot(long generation, byte[] payload) {
        public Snapshot { payload=payload.clone(); }
        @Override public byte[] payload(){return payload.clone();}
    }
    public static final class StaleGenerationException extends IOException {
        public StaleGenerationException(long expected,long actual){super("stale economy generation: expected="+expected+", actual="+actual);}
    }
    public TradeAtomicStore(Path directory,UUID world){this.directory=Objects.requireNonNull(directory);this.world=Objects.requireNonNull(world);}
    private Path snapshot(){return directory.resolve("economy.snapshot");}
    private Path lockPath(){return directory.resolve("economy.lock");}
    public Optional<Snapshot> read() throws IOException {return readSnapshot();}
    private Optional<Snapshot> readSnapshot() throws IOException {
        Path path=snapshot();if(!Files.exists(path))return Optional.empty();
        long size=Files.size(path);
        if(size<64 || size>MAX_BYTES+128)throw new IOException("invalid economy snapshot length");
        byte[] bytes=Files.readAllBytes(path);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION)throw new IOException("unsupported economy snapshot");
            if(in.readLong()!=world.getMostSignificantBits() || in.readLong()!=world.getLeastSignificantBits())throw new IOException("economy snapshot belongs to another world");
            long generation=in.readLong();int length=in.readInt();
            if(generation<1 || length<0 || length>MAX_BYTES || bytes.length!=4+4+16+8+4+length+32)throw new IOException("invalid economy snapshot payload");
            byte[] payload=new byte[length];in.readFully(payload);
            byte[] stored=new byte[32];in.readFully(stored);
            byte[] actual=sha256(Arrays.copyOf(bytes,bytes.length-32));
            if(!MessageDigest.isEqual(stored,actual))throw new IOException("economy snapshot checksum mismatch");
            return Optional.of(new Snapshot(generation,payload));
        }catch(EOFException e){throw new IOException("truncated economy snapshot",e);}
    }
    /** Compare-and-swap commit; one complete snapshot replaces another. */
    public long commit(long expectedGeneration,byte[] payload) throws IOException {
        synchronized(JVM_LOCKS.computeIfAbsent(lockPath().toAbsolutePath().normalize(), k -> new Object())) {
        Objects.requireNonNull(payload);
        if(payload.length>MAX_BYTES || expectedGeneration<0)throw new IllegalArgumentException("invalid snapshot");
        Files.createDirectories(directory);
        try(FileChannel lockChannel=FileChannel.open(lockPath(),CREATE,WRITE);
            FileLock ignored=lockChannel.lock()) {
            long current=readSnapshot().map(Snapshot::generation).orElse(0L);
            if(current!=expectedGeneration)throw new StaleGenerationException(expectedGeneration,current);
            if(current==Long.MAX_VALUE)throw new IOException("generation overflow");
            long next=current+1;
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(raw)){
                out.writeInt(MAGIC);out.writeInt(VERSION);
                out.writeLong(world.getMostSignificantBits());out.writeLong(world.getLeastSignificantBits());
                out.writeLong(next);out.writeInt(payload.length);out.write(payload);
            }
            byte[] data=raw.toByteArray();
            ByteArrayOutputStream complete=new ByteArrayOutputStream();complete.write(data);complete.write(sha256(data));
            Path temp=Files.createTempFile(directory,"economy-",".tmp");
            try {
                try(FileChannel channel=FileChannel.open(temp,WRITE,TRUNCATE_EXISTING)){
                    ByteBuffer buffer=ByteBuffer.wrap(complete.toByteArray());
                    while(buffer.hasRemaining())channel.write(buffer);
                    channel.force(true);
                }
                // No non-atomic fallback: unsupported filesystems must fail closed.
                Files.move(temp,snapshot(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                try(FileChannel dir=FileChannel.open(directory,READ)){dir.force(true);}
            }finally{Files.deleteIfExists(temp);}
            return next;
        }
        }
    }
    private static byte[] sha256(byte[] data){
        try{return MessageDigest.getInstance("SHA-256").digest(data);}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
