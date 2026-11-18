package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Independent, bank-only write-ahead journal. It NEVER replays item mutations.
 * After an unverified restart the bank remains quarantined until all recorded
 * physical contents and separate ledgers have been independently reconciled.
 */
public final class BankOperationLedger {
    public enum Kind {
        PERSONAL_DEPOSIT, PERSONAL_WITHDRAW, CITY_DEPOSIT, CITY_WITHDRAW,
        DONATE, THIRD_PARTY_CREDIT, TAX_PAYMENT, FREE_CASH_ASSIGN
    }
    public enum State { PREPARED, RECEIPT, ABORTED, RECONCILED }

    /** All values in copper units; hashes include item identities and complete NBT. */
    public record Snapshot(long wallet, long vault, long actorAccount, long recipientAccount,
                           long bankReserve, long cityReserve, long treasury, long sovereign,
                           String walletHash, String vaultHash) {
        public Snapshot {
            if(wallet<0||vault<0||actorAccount<0||recipientAccount<0
                    ||bankReserve<0||cityReserve<0||treasury<0||sovereign<0
                    ||!validHash(walletHash)||!validHash(vaultHash))
                throw new IllegalArgumentException("Invalid bank snapshot");
        }
        String encode() {
            return wallet+","+vault+","+actorAccount+","+recipientAccount+","+bankReserve+","
                    +cityReserve+","+treasury+","+sovereign+","+walletHash+","+vaultHash;
        }
        static Snapshot decode(String value) throws IOException {
            String[] p=value.split(",",-1);
            if(p.length!=10)throw new IOException("Invalid snapshot field count");
            try {
                return new Snapshot(Long.parseLong(p[0]),Long.parseLong(p[1]),
                        Long.parseLong(p[2]),Long.parseLong(p[3]),Long.parseLong(p[4]),
                        Long.parseLong(p[5]),Long.parseLong(p[6]),Long.parseLong(p[7]),p[8],p[9]);
            }catch(IllegalArgumentException ex){throw new IOException("Bad bank snapshot",ex);}
        }
    }
    public record Entry(UUID id, UUID town, UUID actor, UUID recipient, Kind kind,
                        long amount, Snapshot before, Snapshot after, State state) {}

    private static final String ZERO_HASH="0".repeat(64);
    private final Path file;
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<UUID,UUID> activeByTown=new LinkedHashMap<>();
    private boolean healthy=true;
    private final java.util.Set<UUID> recoveryRequired=new java.util.HashSet<>();

    private BankOperationLedger(Path file) {this.file=file;}
    public static BankOperationLedger open(Path file) throws IOException {
        if(file==null)throw new IOException("Missing bank journal path");
        BankOperationLedger result=new BankOperationLedger(file);
        if(Files.exists(file)){
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8))
                result.replay(line);
        }
        for(Entry e:result.entries.values())
            if(e.state()==State.PREPARED||e.state()==State.RECEIPT)
                result.recoveryRequired.add(e.id());
        return result;
    }
    public static String zeroHash(){return ZERO_HASH;}

    public synchronized boolean healthy(){return healthy;}
    public synchronized boolean blocked(UUID town) {
        if(!healthy||town==null||activeByTown.containsKey(town))return true;
        for(Entry e:entries.values())if(e.town().equals(town)
                &&e.state()!=State.ABORTED&&e.state()!=State.RECONCILED
                &&(recoveryRequired.contains(e.id())||e.state()==State.PREPARED))return true;
        return false;
    }
    public synchronized List<Entry> unresolved() {
        List<Entry> result=new ArrayList<>();
        for(Entry e:entries.values())if(e.state()!=State.ABORTED&&e.state()!=State.RECONCILED
                &&(recoveryRequired.contains(e.id())||e.state()==State.PREPARED))result.add(e);
        return List.copyOf(result);
    }
    public synchronized Entry find(UUID id){return entries.get(id);}
    public synchronized Entry prepare(UUID town,UUID actor,UUID recipient,Kind kind,long amount,
                                      Snapshot before) {
        if(blocked(town)||actor==null||recipient==null||kind==null||before==null
                ||amount<1||amount>1_000_000)return null;
        UUID id=UUID.randomUUID();
        Entry entry=new Entry(id,town,actor,recipient,kind,amount,before,null,State.PREPARED);
        if(!append("P|"+id+"|"+town+"|"+actor+"|"+recipient+"|"+kind+"|"+amount+"|"+before.encode()))
            return null;
        entries.put(id,entry);
        activeByTown.put(town,id);
        return entry;
    }
    public synchronized boolean receipt(Entry tx,Snapshot after) {
        if(!isActive(tx)||after==null||!matches(tx,after))return false;
        if(!append("C|"+tx.id()+"|"+after.encode()))return false;
        entries.put(tx.id(),new Entry(tx.id(),tx.town(),tx.actor(),tx.recipient(),
                tx.kind(),tx.amount(),tx.before(),after,State.RECEIPT));
        activeByTown.remove(tx.town());
        return true;
    }
    public synchronized boolean abortUnchanged(Entry tx,Snapshot observed) {
        if(!isActive(tx)||!tx.before().equals(observed))return false;
        if(!append("N|"+tx.id()))return false;
        entries.put(tx.id(),new Entry(tx.id(),tx.town(),tx.actor(),tx.recipient(),
                tx.kind(),tx.amount(),tx.before(),null,State.ABORTED));
        activeByTown.remove(tx.town());
        recoveryRequired.remove(tx.id());
        return true;
    }
    /**
     * OP-only API, called AFTER physical & account verification by server-side
     * recovery command. Does not issue coins, correct balances or replay writes.
     */
    public synchronized boolean reconcile(UUID id,Snapshot observed) {
        Entry tx=entries.get(id);
        if(!healthy||tx==null||observed==null||!recoveryRequired.contains(id)
                ||tx.state()==State.RECONCILED||tx.state()==State.ABORTED)return false;
        boolean confirmed=tx.state()==State.RECEIPT&&tx.after()!=null&&tx.after().equals(observed);
        boolean neverApplied=tx.state()==State.PREPARED&&tx.before().equals(observed);
        if(!confirmed&&!neverApplied)return false;
        if(!append("A|"+id))return false;
        entries.put(id,new Entry(tx.id(),tx.town(),tx.actor(),tx.recipient(),
                tx.kind(),tx.amount(),tx.before(),tx.after(),State.RECONCILED));
        recoveryRequired.remove(id);
        activeByTown.remove(tx.town());
        return true;
    }
    private boolean isActive(Entry tx) {
        return healthy&&tx!=null&&tx.state()==State.PREPARED
                &&tx.id().equals(activeByTown.get(tx.town()))&&entries.get(tx.id())==tx;
    }
    /** Ledger values are compared as signed deltas, never as minted coin counts. */
    static boolean matches(Entry tx,Snapshot a) {
        Snapshot b=tx.before();
        long n=tx.amount();
        try {
            long wallet=b.wallet(), vault=b.vault(), owner=b.actorAccount(),
                    recipient=b.recipientAccount(), bank=b.bankReserve(),
                    city=b.cityReserve(), treasury=b.treasury();
            switch(tx.kind()) {
                case PERSONAL_DEPOSIT -> {
                    wallet=Math.subtractExact(wallet,n);vault=Math.addExact(vault,n);
                    owner=Math.addExact(owner,n);recipient=Math.addExact(recipient,n);
                    bank=Math.addExact(bank,n);
                }
                case PERSONAL_WITHDRAW -> {
                    wallet=Math.addExact(wallet,n);vault=Math.subtractExact(vault,n);
                    owner=Math.subtractExact(owner,n);recipient=Math.subtractExact(recipient,n);
                    bank=Math.subtractExact(bank,n);
                }
                case CITY_DEPOSIT,TAX_PAYMENT -> {
                    wallet=Math.subtractExact(wallet,n);vault=Math.addExact(vault,n);
                    city=Math.addExact(city,n);treasury=Math.addExact(treasury,n);
                }
                case CITY_WITHDRAW -> {
                    wallet=Math.addExact(wallet,n);vault=Math.subtractExact(vault,n);
                    city=Math.subtractExact(city,n);treasury=Math.subtractExact(treasury,n);
                }
                case FREE_CASH_ASSIGN -> {
                    city=Math.addExact(city,n);treasury=Math.addExact(treasury,n);
                }
                case DONATE -> {
                    if(!a.walletHash().equals(b.walletHash())||!a.vaultHash().equals(b.vaultHash()))return false;
                    owner=Math.subtractExact(owner,n);recipient=Math.subtractExact(recipient,n);
                    bank=Math.subtractExact(bank,n);city=Math.addExact(city,n);
                    treasury=Math.addExact(treasury,n);
                }
                case THIRD_PARTY_CREDIT -> {
                    wallet=Math.subtractExact(wallet,n);vault=Math.addExact(vault,n);
                    recipient=Math.addExact(recipient,n);bank=Math.addExact(bank,n);
                    if(tx.actor().equals(tx.recipient()))owner=Math.addExact(owner,n);
                }
            }
            return a.wallet()==wallet&&a.vault()==vault&&a.actorAccount()==owner
                    &&a.recipientAccount()==recipient&&a.bankReserve()==bank
                    &&a.cityReserve()==city&&a.treasury()==treasury
                    &&a.sovereign()==b.sovereign();
        }catch(ArithmeticException overflow){return false;}
    }
    private static boolean validHash(String hash) {
        return hash!=null&&hash.matches("[0-9a-f]{64}");
    }
    private boolean append(String payload) {
        if(!healthy)return false;
        byte[] bytes=(payload+"|"+crc(payload)+"\n").getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(file.getParent());
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,StandardOpenOption.WRITE)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            return true;
        }catch(IOException failure){
            healthy=false;
            System.err.println("[Kingdom bank] Journal I/O error; all bank writes locked: "+failure);
            return false;
        }
    }
    private static String crc(String value) {
        CRC32 crc=new CRC32();crc.update(value.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
    private void replay(String line) throws IOException {
        int split=line.lastIndexOf('|');
        if(split<1||!crc(line.substring(0,split)).equals(line.substring(split+1)))
            throw new IOException("Damaged bank journal: checksum");
        String[] p=line.substring(0,split).split("\\|",-1);
        try {
            switch(p[0]){
                case "P" -> {
                    if(p.length!=8)throw new IOException("Invalid bank prepare");
                    UUID id=UUID.fromString(p[1]),town=UUID.fromString(p[2]);
                    if(entries.containsKey(id))throw new IOException("Duplicate bank operation");
                    Entry e=new Entry(id,town,UUID.fromString(p[3]),UUID.fromString(p[4]),
                            Kind.valueOf(p[5]),Long.parseLong(p[6]),Snapshot.decode(p[7]),null,State.PREPARED);
                    if(e.amount()<1||e.amount()>1_000_000)throw new IOException("Invalid amount");
                    entries.put(id,e);
                }
                case "C" -> {
                    if(p.length!=3)throw new IOException("Invalid bank receipt");
                    UUID id=UUID.fromString(p[1]);Entry e=entries.get(id);
                    if(e==null||e.state()!=State.PREPARED)throw new IOException("Unexpected receipt");
                    Snapshot after=Snapshot.decode(p[2]);
                    if(!matches(e,after))throw new IOException("Invalid bank receipt values");
                    entries.put(id,new Entry(e.id(),e.town(),e.actor(),e.recipient(),
                            e.kind(),e.amount(),e.before(),after,State.RECEIPT));
                }
                case "N","A" -> {
                    if(p.length!=2)throw new IOException("Invalid terminal marker");
                    UUID id=UUID.fromString(p[1]);Entry e=entries.get(id);
                    if(e==null||e.state()==State.RECONCILED||e.state()==State.ABORTED
                            ||(p[0].equals("N")&&e.state()!=State.PREPARED))
                        throw new IOException("Unexpected bank terminal marker");
                    entries.put(id,new Entry(e.id(),e.town(),e.actor(),e.recipient(),
                            e.kind(),e.amount(),e.before(),e.after(),
                            p[0].equals("N")?State.ABORTED:State.RECONCILED));
                }
                default -> throw new IOException("Unknown bank record");
            }
        }catch(IllegalArgumentException ex){throw new IOException("Invalid bank journal data",ex);}
    }
}
