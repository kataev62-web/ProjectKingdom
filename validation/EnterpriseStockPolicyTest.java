import ru.projectkingdom.npc.EnterpriseStockPolicy;
public class EnterpriseStockPolicyTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  var p=new EnterpriseStockPolicy.Thresholds(5,20);
  check(EnterpriseStockPolicy.nextProduction(p,3,false));
  check(EnterpriseStockPolicy.nextProduction(p,12,true));
  check(!EnterpriseStockPolicy.nextProduction(p,20,true));
  check(!EnterpriseStockPolicy.nextProduction(p,12,false));
  check(EnterpriseStockPolicy.nextProduction(p,5,false));
  check(EnterpriseStockPolicy.productionBatch(p,19,true,4)==1);
  check(EnterpriseStockPolicy.productionBatch(p,20,true,4)==0);
  var raw=new EnterpriseStockPolicy.Thresholds(20,100);
  check(EnterpriseStockPolicy.reorderQuantity(raw,15,0)==85);
  check(EnterpriseStockPolicy.reorderQuantity(raw,15,40)==45);
  check(EnterpriseStockPolicy.reorderQuantity(raw,30,0)==0);
  check(EnterpriseStockPolicy.reorderQuantity(raw,15,90)==0);
  try {new EnterpriseStockPolicy.Thresholds(10,5);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS EnterpriseStockPolicy");
 }
}
