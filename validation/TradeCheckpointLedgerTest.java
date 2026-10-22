import ru.projectkingdom.npc.TradeCheckpointLedger;
import java.nio.file.*;
import java.util.*;
public class TradeCheckpointLedgerTest {
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args) throws Exception {
        Path p=Files.createTempDirectory("pk-checkpoint-test-");
        TradeCheckpointLedger ledger=new TradeCheckpointLedger(p);
        UUID id=UUID.randomUUID(); String digest="a".repeat(64);
        check(!ledger.matches(id,digest,"worldA",5),"missing checkpoint cannot unlock");
        ledger.record(new TradeCheckpointLedger.Receipt(id,digest,"worldA",5));
        check(ledger.matches(id,digest,"worldA",5),"matching receipt");
        check(!ledger.matches(id,digest,"worldB",5),"wrong world");
        check(!ledger.matches(id,digest,"worldA",4),"stale save epoch");
        check(!ledger.matches(id,"b".repeat(64),"worldA",5),"wrong digest");
        try{ledger.record(new TradeCheckpointLedger.Receipt(id,digest,"worldA",6));throw new AssertionError("duplicate allowed");}
        catch(FileAlreadyExistsException expected){}
        // Two independent ledger instances racing to publish the same ID must
        // never replace the winner's receipt, even on Unix filesystems.
        UUID concurrentId=UUID.randomUUID();
        var start=new java.util.concurrent.CountDownLatch(1);
        var success=new java.util.concurrent.atomic.AtomicInteger();
        var duplicate=new java.util.concurrent.atomic.AtomicInteger();
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        java.util.List<Thread> writers=new java.util.ArrayList<>();
        for(int i=0;i<12;i++) {
            final int epoch=i+1;
            Thread thread=new Thread(()->{
                try {
                    start.await();
                    new TradeCheckpointLedger(p).record(new TradeCheckpointLedger.Receipt(
                        concurrentId,digest,"worldA",epoch));
                    success.incrementAndGet();
                } catch(FileAlreadyExistsException expected) { duplicate.incrementAndGet(); }
                  catch(Throwable unexpected) { failure.compareAndSet(null,unexpected); }
            });
            writers.add(thread);thread.start();
        }
        start.countDown();for(Thread thread:writers)thread.join();
        check(failure.get()==null,"unexpected concurrent failure: "+failure.get());
        check(success.get()==1 && duplicate.get()==11,"one and only one writer wins");
        check(new TradeCheckpointLedger(p).read(concurrentId).isPresent(),"winner readable");
        Path f=p.resolve(id+".checkpoint");Files.writeString(f,Files.readString(f).replace(digest,"b".repeat(64)));
        try{ledger.read(id);throw new AssertionError("tampering accepted");}
        catch(java.io.IOException expected){}
        System.out.println("PASS checkpoint receipt tests (7 assertions)");
    }
}
