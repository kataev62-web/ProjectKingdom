package ru.projectkingdom.npc;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Canonical, all-or-nothing economy state for opted-in inventories and accounts.
 * This class is independent of Minecraft's asynchronously saved chunk inventories.
 * Callers MUST NOT mirror the same authoritative stock to independently saved
 * block-entity containers without an explicit reconciliation protocol.
 */
public final class TradeAtomicEconomy {
    private static final int FORMAT = 1;
    private static final int MAX_ENTITIES = 100_000;
    private static final int MAX_SLOTS = 4096;
    private static final int MAX_ITEM_BYTES = 65536;
    public record Stack(String item, byte[] tag, int count, int maxCount) {
        public Stack {
            Objects.requireNonNull(item); Objects.requireNonNull(tag);
            if(item.isBlank() || item.length()>256 || tag.length>MAX_ITEM_BYTES || count<1 || maxCount<1 || maxCount>64 || count>maxCount)
                throw new IllegalArgumentException("invalid stack");
            tag=tag.clone();
        }
        @Override public byte[] tag(){return tag.clone();}
        public boolean sameItem(Stack other){return item.equals(other.item) && Arrays.equals(tag,other.tag) && maxCount==other.maxCount;}
    }
    public record Account(UUID id,long copper,List<Stack> slots) {
        public Account {
            Objects.requireNonNull(id); Objects.requireNonNull(slots);
            if(copper<0 || slots.size()>MAX_SLOTS)throw new IllegalArgumentException("invalid account");
            slots=Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }
    public record State(long generation,Map<UUID,Account> accounts,Set<UUID> completed) {
        public State {
            accounts=Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
            completed=Collections.unmodifiableSet(new LinkedHashSet<>(completed));
        }
    }
    public enum Outcome { COMMITTED, ALREADY_COMMITTED, INVALID, INSUFFICIENT_FUNDS, INSUFFICIENT_STOCK, NO_SPACE, STALE_VIEW }
    private final TradeAtomicStore store;
    public TradeAtomicEconomy(Path path,UUID world){store=new TradeAtomicStore(path,world);}
    public State read() throws IOException {
        var snap=store.read();
        return snap.isEmpty()?new State(0,Map.of(),Set.of()):decode(snap.get());
    }
    public long initialize(Map<UUID,Account> accounts) throws IOException {
        if(accounts.size()>MAX_ENTITIES || accounts.entrySet().stream().anyMatch(e->e.getKey()==null || e.getValue()==null || !e.getKey().equals(e.getValue().id())))
            throw new IllegalArgumentException("invalid accounts");
        return store.commit(0,encode(accounts,Set.of()));
    }
    /** One atomic durable commit includes both cash balances and physical item slots. */
    public Outcome purchase(UUID tx,UUID buyerId,UUID sellerId,int sourceSlot,int count,long unitPrice) throws IOException {
        return purchaseInternal(tx,buyerId,sellerId,sourceSlot,count,unitPrice,-1);
    }
    /** Reject a physical preflight whose authoritative snapshot changed before commit. */
    public Outcome purchaseAtGeneration(UUID tx,UUID buyerId,UUID sellerId,int sourceSlot,int count,long unitPrice,long generation) throws IOException {
        return purchaseInternal(tx,buyerId,sellerId,sourceSlot,count,unitPrice,generation);
    }
    private Outcome purchaseInternal(UUID tx,UUID buyerId,UUID sellerId,int sourceSlot,int count,long unitPrice,long generation) throws IOException {
        if(tx==null||buyerId==null||sellerId==null||buyerId.equals(sellerId)||count<=0||count>64||unitPrice<=0||unitPrice>1_000_000)
            return Outcome.INVALID;
        for(int attempt=0;attempt<32;attempt++){
        State before=read();
        if(before.completed().contains(tx))return Outcome.ALREADY_COMMITTED;
        if(generation>=0 && before.generation()!=generation)return Outcome.STALE_VIEW;
        Account buyer=before.accounts().get(buyerId),seller=before.accounts().get(sellerId);
        if(buyer==null||seller==null||sourceSlot<0||sourceSlot>=seller.slots().size())return Outcome.INVALID;
        Stack source=seller.slots().get(sourceSlot);
        if(source==null||source.count()<count)return Outcome.INSUFFICIENT_STOCK;
        long total;
        try{total=Math.multiplyExact(unitPrice,count);}catch(ArithmeticException ex){return Outcome.INVALID;}
        if(buyer.copper()<total)return Outcome.INSUFFICIENT_FUNDS;
        long sellerCash;
        try{sellerCash=Math.addExact(seller.copper(),total);}catch(ArithmeticException ex){return Outcome.INVALID;}
        List<Stack> incoming=new ArrayList<>(buyer.slots());
        int remaining=count;
        for(int i=0;i<incoming.size()&&remaining>0;i++){
            Stack s=incoming.get(i);
            if(s!=null && s.sameItem(source) && s.count()<s.maxCount()){
                int n=Math.min(remaining,s.maxCount()-s.count());
                incoming.set(i,new Stack(s.item(),s.tag(),s.count()+n,s.maxCount()));remaining-=n;
            }
        }
        for(int i=0;i<incoming.size()&&remaining>0;i++)if(incoming.get(i)==null){
            int n=Math.min(remaining,source.maxCount());
            incoming.set(i,new Stack(source.item(),source.tag(),n,source.maxCount()));remaining-=n;
        }
        if(remaining>0)return Outcome.NO_SPACE;
        List<Stack> outgoing=new ArrayList<>(seller.slots());
        outgoing.set(sourceSlot,source.count()==count?null:new Stack(source.item(),source.tag(),source.count()-count,source.maxCount()));
        Map<UUID,Account> accounts=new LinkedHashMap<>(before.accounts());
        accounts.put(buyerId,new Account(buyerId,buyer.copper()-total,incoming));
        accounts.put(sellerId,new Account(sellerId,sellerCash,outgoing));
        Set<UUID> completed=new LinkedHashSet<>(before.completed());completed.add(tx);
        try {store.commit(before.generation(),encode(accounts,completed));
            return Outcome.COMMITTED;
        } catch(TradeAtomicStore.StaleGenerationException conflict) {
            // A physical preflight cannot be retried against newer data without re-reading containers.
            if(generation>=0)return Outcome.STALE_VIEW;
            // Canonical-only callers can safely re-read stock, funds and receipts.
        }
        }
        throw new IOException("too many concurrent economy updates");
    }
    private static byte[] encode(Map<UUID,Account> accounts,Set<UUID> completed) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(FORMAT);out.writeInt(accounts.size());
            for(Account a:accounts.values()){
                writeUuid(out,a.id());out.writeLong(a.copper());out.writeInt(a.slots().size());
                for(Stack s:a.slots()){
                    out.writeBoolean(s!=null);
                    if(s!=null){out.writeUTF(s.item());out.writeInt(s.tag().length);out.write(s.tag());out.writeInt(s.count());out.writeInt(s.maxCount());}
                }
            }
            out.writeInt(completed.size());for(UUID id:completed)writeUuid(out,id);
        }
        return bytes.toByteArray();
    }
    private static State decode(TradeAtomicStore.Snapshot snap) throws IOException {
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(snap.payload()))){
            if(in.readInt()!=FORMAT)throw new IOException("unsupported economy payload");
            int n=in.readInt();if(n<0||n>MAX_ENTITIES)throw new IOException("invalid account count");
            Map<UUID,Account> accounts=new LinkedHashMap<>();
            for(int k=0;k<n;k++){
                UUID id=readUuid(in);long cash=in.readLong();int slots=in.readInt();
                if(cash<0||slots<0||slots>MAX_SLOTS)throw new IOException("invalid account");
                List<Stack> inventory=new ArrayList<>();
                for(int j=0;j<slots;j++){
                    if(!in.readBoolean()){inventory.add(null);continue;}
                    String item=in.readUTF();int len=in.readInt();if(len<0||len>MAX_ITEM_BYTES)throw new IOException("invalid item tag");
                    byte[] tag=new byte[len];in.readFully(tag);
                    inventory.add(new Stack(item,tag,in.readInt(),in.readInt()));
                }
                if(accounts.put(id,new Account(id,cash,inventory))!=null)throw new IOException("duplicate account");
            }
            int count=in.readInt();if(count<0||count>1_000_000)throw new IOException("invalid receipts");
            Set<UUID> receipts=new LinkedHashSet<>();for(int j=0;j<count;j++)if(!receipts.add(readUuid(in)))throw new IOException("duplicate receipt");
            if(in.available()!=0)throw new IOException("trailing economy bytes");
            return new State(snap.generation(),accounts,receipts);
        }catch(IllegalArgumentException ex){throw new IOException("invalid economy payload",ex);}
    }
    private static void writeUuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID readUuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
}
