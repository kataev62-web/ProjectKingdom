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
  var state=new EnterpriseStockPolicy.StockState(3,4,2,true);
  check(state.committedTotal()==9);
  check(EnterpriseStockPolicy.nextProduction(p,state,true));
  check(!EnterpriseStockPolicy.nextProduction(p,new EnterpriseStockPolicy.StockState(19,1,0,true),true));
  check(EnterpriseStockPolicy.productionBatch(p,new EnterpriseStockPolicy.StockState(15,2,2,true),true,4)==1);
  check(EnterpriseStockPolicy.reorderQuantity(new EnterpriseStockPolicy.Thresholds(20,100),
      new EnterpriseStockPolicy.StockState(15,40,0,true))==45);
  var unknown=new EnterpriseStockPolicy.StockState(0,0,0,false);
  check(!EnterpriseStockPolicy.nextProduction(p,unknown,false));
  check(EnterpriseStockPolicy.productionBatch(p,unknown,true,4)==0);
  check(EnterpriseStockPolicy.reorderQuantity(p,unknown)==0);
  check(EnterpriseStockPolicy.reorderQuantity(new EnterpriseStockPolicy.Thresholds(0,100),0,0)==0);
  check(EnterpriseStockPolicy.reorderQuantity(new EnterpriseStockPolicy.Thresholds(5,20),5,0)==15);
  try {new EnterpriseStockPolicy.StockState(-1,0,0,true);throw new AssertionError();}catch(IllegalArgumentException expected){}
  var raw=new EnterpriseStockPolicy.Thresholds(20,100);
  check(EnterpriseStockPolicy.reorderQuantity(raw,15,0)==85);
  check(EnterpriseStockPolicy.reorderQuantity(raw,15,40)==45);
  check(EnterpriseStockPolicy.reorderQuantity(raw,30,0)==0);
  check(EnterpriseStockPolicy.reorderQuantity(raw,15,90)==0);
  try {new EnterpriseStockPolicy.Thresholds(10,5);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS EnterpriseStockPolicy");
 }
}
