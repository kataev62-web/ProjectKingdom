import ru.projectkingdom.npc.RegionalMarketPricing;
import java.util.*;
public class RegionalMarketPricingTest {
 static void check(boolean ok) { if (!ok) throw new AssertionError(); }
 public static void main(String[] args) {
  var q=RegionalMarketPricing.quote(100);
  check(q.normalMax()==120 && q.shortageMax()==150);
  check(RegionalMarketPricing.updateDaily(100,List.of(new RegionalMarketPricing.Sale(150,10,false)))==105);
  check(RegionalMarketPricing.updateDaily(100,List.of(new RegionalMarketPricing.Sale(10000,10,false),new RegionalMarketPricing.Sale(140,1,true)))==100);
  check(RegionalMarketPricing.updateDaily(100,List.of(new RegionalMarketPricing.Sale(50,20,false)))==95);
  var good=new RegionalMarketPricing.Request(1200,2,5,20,10,120,false,false);
  check(RegionalMarketPricing.canNpcBuy(q,good));
  check(!RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(1200,2,5,20,10,121,false,false)));
  check(RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(1500,2,5,20,10,150,true,false)));
  check(!RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(1500,5,5,20,10,100,false,false)));
  check(!RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(1500,2,5,11,10,100,false,false)));
  check(!RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(1,2,5,20,10,100,false,true)));
  check(RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(2000,2,5,20,10,200,false,true)));
  check(!RegionalMarketPricing.canNpcBuy(q,new RegionalMarketPricing.Request(Long.MAX_VALUE,0,1,100,100,Long.MAX_VALUE,true,true)));
  check(!new RegionalMarketPricing.Key(UUID.randomUUID(),"minecraft:iron_ingot").item().isEmpty());
  System.out.println("PASS regional market pricing (13 assertions)");
 }
}
