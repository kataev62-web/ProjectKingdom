import ru.projectkingdom.npc.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TradePhysicalProjectionCoordinatorTest {
  static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
  static class Physical implements TradePhysicalProjectionCoordinator.PhysicalWriter {
    final Map<UUID,TradeAtomicEconomy.Account> data=new HashMap<>();
    int writes=0; boolean failSecond=false;
    public TradeAtomicEconomy.Account read(UUID id){return data.get(id);}
    public boolean compareAndSet(UUID id,TradeAtomicEconomy.Account before,TradeAtomicEconomy.Account after)throws IOException{
      if(failSecond && writes==1)throw new IOException("simulated crash");
      var current=data.get(id);
      if(!same(current,before))return false;
      data.put(id,after);writes++;return true;
    }
    static boolean same(TradeAtomicEconomy.Account a,TradeAtomicEconomy.Account b){
      if(a==null||b==null)return a==b;
      if(!a.id().equals(b.id())||a.copper()!=b.copper()||a.slots().size()!=b.slots().size())return false;
      for(int i=0;i<a.slots().size();i++){
        var x=a.slots().get(i);var y=b.slots().get(i);
        if(x==null||y==null){if(x!=y)return false;}
        else if(x.count()!=y.count()||!x.sameItem(y))return false;
      }
      return true;
    }
  }
  public static void main(String[] args)throws Exception {
    var dir=Files.createTempDirectory("physical-projection-");
    var buyer=UUID.randomUUID();var seller=UUID.randomUUID();var tx=UUID.randomUUID();
    var economy=new TradeAtomicEconomy(dir.resolve("economy"),UUID.randomUUID());
    var b=new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null));
    var s=new TradeAtomicEconomy.Account(seller,0,List.of(new TradeAtomicEconomy.Stack("minecraft:stone",new byte[]{1},2,64)));
    economy.initialize(Map.of(buyer,b,seller,s));
    var p=new Physical();p.data.put(buyer,b);p.data.put(seller,s);
    ok(TradePhysicalProjectionCoordinator.project(economy,tx,b,s,p)==TradePhysicalProjectionCoordinator.Result.INVALID_RECEIPT,"missing receipt");
    ok(p.writes==0,"no writes without receipt");
    ok(economy.purchase(tx,buyer,seller,0,1,10)==TradeAtomicEconomy.Outcome.COMMITTED,"purchase");
    p.failSecond=true;
    try{TradePhysicalProjectionCoordinator.project(economy,tx,b,s,p);throw new AssertionError("crash not injected");}catch(IOException expected){}
    ok(p.writes==1,"one side written before crash");
    p.failSecond=false;
    ok(TradePhysicalProjectionCoordinator.project(economy,tx,b,s,p)==TradePhysicalProjectionCoordinator.Result.NEEDS_MANUAL_REVIEW,"mixed state must not replay");
    ok(p.writes==1,"no second write on restart");
    var after=economy.read();p.data.put(buyer,b);p.data.put(seller,s);p.writes=0;
    ok(TradePhysicalProjectionCoordinator.project(economy,tx,b,s,p)==TradePhysicalProjectionCoordinator.Result.APPLIED,"apply both");
    ok(p.writes==2,"both written");
    ok(TradePhysicalProjectionCoordinator.project(economy,tx,b,s,p)==TradePhysicalProjectionCoordinator.Result.ALREADY_APPLIED,"idempotent");
    ok(p.writes==2,"no replay");
    p.data.remove(buyer);
    ok(TradePhysicalProjectionCoordinator.project(economy,tx,b,s,p)==TradePhysicalProjectionCoordinator.Result.UNAVAILABLE,"unloaded");
    System.out.println("PASS physical projection coordinator 10 checks");
  }
}
