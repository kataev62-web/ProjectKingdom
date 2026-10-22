package ru.projectkingdom.npc;
import java.util.*;import net.minecraft.core.*;import net.minecraft.core.registries.*;import net.minecraft.nbt.*;import net.minecraft.server.*;import net.minecraft.server.level.*;import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.level.block.entity.*;
public class Stage1LogicHarness {
 static void yes(boolean v,String message){if(!v)throw new AssertionError(message);}
 static class Chest extends BlockEntity implements Container {
   final ItemStack[] cells=new ItemStack[4];Chest(){Arrays.fill(cells,ItemStack.EMPTY);}
   public ItemStack getItem(int i){return cells[i];}public void setItem(int i,ItemStack s){cells[i]=s;}
 }
 public static void main(String[] args)throws Exception {
  var s=new MinecraftServer();var mayor=UUID.randomUUID();var worker=UUID.randomUUID();var owner2=UUID.randomUUID();
  var town=UUID.randomUUID();var city=KingdomCivicsData.get(s);
  city.towns.put(town,new KingdomCivicsData.Town(town,"Town",mayor,null,12000));city.members.put(mayor,town);city.members.put(worker,town);
  var seller=new ServerPlayer(s,mayor);seller.inventory.wallet.money=1200;
  var chest=new Chest();var pos=new BlockPos(0,0,0);s.world.entities.put(pos,chest);
  var buyer=new ServerPlayer(s,owner2);buyer.inventory.wallet.money=1000;
  var chest2=new Chest();var pos2=new BlockPos(2,0,0);s.world.entities.put(pos2,chest2);
  var db=KingdomEnterpriseData.get(s);
  var first=db.create(mayor,"Smith");var second=db.create(mayor,"Bakery");
  yes(db.ownedAll(mayor).size()==2,"two companies");yes(db.owned(mayor).id().equals(second.id()),"active newly created");
  yes(db.select(mayor,first.id()),"select firm");yes(db.bindCounter(seller,pos),"register cashbox");
  yes(db.deposit(seller,250),"deposit");yes(db.owned(mayor).cash()==250,"firm backed credit");
  yes(db.withdraw(seller,50),"withdraw");yes(seller.inventory.wallet.money==1000,"copper conserved");
  seller.pos=new BlockPos(999,0,999);yes(!db.withdraw(seller,10),"must be at cashbox");seller.pos=new BlockPos(0,1,0);
  var save=db.save(new CompoundTag());var load=KingdomEnterpriseData.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
  var reloaded=(KingdomEnterpriseData)load.invoke(null,save);
  yes(reloaded.ownedAll(mayor).size()==2&&reloaded.owned(mayor).id().equals(first.id()),"persistence/selection");
  var buyerFirm=db.create(owner2,"Market");yes(db.bindCounter(buyer,pos2),"buyer cashbox");
  yes(db.deposit(buyer,500),"buyer deposit");
  chest.setItem(0,new ItemStack(BuiltInRegistries.ITEM.get("minecraft:charcoal"),32));
  var offer=db.listItem(seller,"minecraft:charcoal",15);yes(offer!=null,"trade offer physical stock");
  long before=db.owned(mayor).cash()+db.owned(owner2).cash();
  yes(db.buy(buyer,offer.id(),6),"trade succeeds");
  yes(db.owned(mayor).cash()+db.owned(owner2).cash()==before,"trade conserves copper");
  yes(db.stock(s.world,first.id(),"minecraft:charcoal")==26 && db.stock(s.world,buyerFirm.id(),"minecraft:charcoal")==6,"physical cargo moved");
  yes(!db.buy(buyer,offer.id(),60),"oversell rejected");
  yes(db.stock(s.world,first.id(),"minecraft:charcoal")==26,"no ghost cargo");
  yes(!db.select(owner2,first.id()),"cannot select somebody else's firm");
  long cashBeforeFailure=db.find(buyerFirm.id()).cash();
  var charcoal=BuiltInRegistries.ITEM.get("minecraft:charcoal");
  chest2.setItem(0,new ItemStack(charcoal,64));
  for(int i=1;i<4;i++)chest2.setItem(i,new ItemStack(BuiltInRegistries.ITEM.get("minecraft:coal"),64));
  yes(!db.buy(buyer,offer.id(),1),"full destination container refuses sale");
  yes(db.find(buyerFirm.id()).cash()==cashBeforeFailure && db.stock(s.world,first.id(),"minecraft:charcoal")==26,
      "failed sale cannot remove cash or goods");
  var quarantined=db.save(new CompoundTag());
  var pending=new CompoundTag();var pendingId=UUID.randomUUID();
  pending.putUUID("Id",pendingId);pending.putUUID("Offer",offer.id());
  pending.putUUID("Seller",first.id());pending.putUUID("Buyer",buyerFirm.id());
  pending.putInt("Count",1);pending.putLong("Price",15);pending.putString("State","PENDING");
  quarantined.getList("Trades",Tag.TAG_COMPOUND).add(pending);
  var resumed=(KingdomEnterpriseData)load.invoke(null,quarantined);
  yes(resumed.unresolved(first.id()),"interrupted trade quarantined after restart");
  yes(java.util.Arrays.stream(KingdomEnterpriseData.class.getMethods())
      .noneMatch(m -> m.getName().equals("acknowledgeReconciled")),
      "unverified public reconciliation bypass is not exposed");
  yes(resumed.unresolved(first.id()),"unverified legacy trade stays quarantined");
  yes(db.invoice(owner2,mayor,999)==null,"unsafe legacy invoice blocked");
  var jobs=KingdomEnterprisePayrollData.get(s);
  yes(jobs.hire(s,mayor,first.id(),worker,50),"company hire");
  s.world.day=2*24000L;
  yes(jobs.accrue(s,first.id(),worker)==100,"company payroll accrual");
  yes(jobs.dismiss(s,mayor,first.id(),worker),"company fire");
  yes(jobs.accrue(s,first.id(),worker)==100,"company old wage saved");
  var employee=new ServerPlayer(s,worker);employee.inventory.wallet.money=1;
  yes(jobs.claim(s,employee,first.id()),"company wage manually paid at counter");
  yes(jobs.accrue(s,first.id(),worker)==0,"company debt not double paid");
  var npcRegistry=KingdomNpcData.get(s);var npcUuid=UUID.randomUUID();
  npcRegistry.add(new KingdomNpcData.Worker(npcUuid,mayor));
  // Wage claim requires physical NPC presence at seller's registered desk.
  var villager=new net.minecraft.world.entity.npc.Villager();
  s.world.people.put(npcUuid,villager);
  jobs.syncNpc(s,npcRegistry);
  yes(jobs.contract(first.id(),npcUuid)!=null,"npc synced to owner's active business");
  villager.position=new BlockPos(1000,0,1000);
  s.world.day=3*24000L;jobs.syncNpc(s,npcRegistry);
  yes(jobs.contract(first.id(),npcUuid).owed()==10,"npc earned debt while far from desk");
  long corporateCashBefore=db.find(first.id()).cash();
  villager.position=new BlockPos(0,0,0);jobs.syncNpc(s,npcRegistry);
  yes(jobs.contract(first.id(),npcUuid).owed()==0,"NPC salary paid only at real cash desk");
  yes(npcRegistry.all().iterator().next().wagePurse.stream().mapToLong(c->KingdomCurrency.value(c)*c.getCount()).sum()==10,
      "NPC received physical ItemStack wages");
  yes(db.find(first.id()).cash()==corporateCashBefore-10,"NPC payroll came from real firm budget");
  npcRegistry.all().iterator().next().employer=null;jobs.syncNpc(s,npcRegistry);
  yes(!jobs.contract(first.id(),npcUuid).active() && jobs.contract(first.id(),npcUuid).owed()==0,"npc contract closed after paid day");
  var jobLoad=KingdomEnterprisePayrollData.class.getDeclaredMethod("load",CompoundTag.class);jobLoad.setAccessible(true);
  var restoredJobs=(KingdomEnterprisePayrollData)jobLoad.invoke(null,jobs.save(new CompoundTag()));
  yes(restoredJobs.contract(first.id(),npcUuid)!=null,"npc job persists restart");
  var p=KingdomPayrollData.get(s);yes(p.hire(s,mayor,worker,30),"hire");
  s.world.day=6*24000L;yes(p.accrue(s,worker)==90,"3 days salary");
  yes(p.dismiss(s,mayor,worker),"dismiss");yes(p.contract(worker)==null,"job removed");
  yes(p.accrue(s,worker)==90,"old wage debt survives");
  var wp=new ServerPlayer(s,worker);wp.inventory.wallet.money=1;
  yes(p.claim(s,wp),"claim after dismissal");yes(wp.inventory.wallet.money==91,"salary deducted from treasury");
  yes(p.accrue(s,worker)==0,"debt cleared once");yes(!p.claim(s,wp),"repeat salary denied");
  s.world.day=7*24000L;yes(p.hire(s,mayor,worker,10),"rehire");
  s.world.day=8*24000L;yes(p.hire(s,mayor,worker,20),"increase");
  yes(p.accrue(s,worker)==10,"old rate 1 day");
  s.world.day=9*24000L;yes(p.accrue(s,worker)==30,"new rate 1 day");
  var payrollLoad=KingdomPayrollData.class.getDeclaredMethod("load",CompoundTag.class);payrollLoad.setAccessible(true);
  yes(((KingdomPayrollData)payrollLoad.invoke(null,p.save(new CompoundTag()))).accrue(s,worker)==30,"payroll persistence");
  var taxes=KingdomTaxData.get(s);
  yes(taxes.owed(s,worker)==0,"tax starts at first assessment");
  s.world.day=12*24000L;yes(taxes.owed(s,worker)==30,"tax 3 days");
  yes(taxes.setRate(s,mayor,20),"mayor changes tax");
  s.world.day=13*24000L;yes(taxes.owed(s,worker)==40,"rate starts next day");
  s.world.day=14*24000L;yes(taxes.owed(s,worker)==60,"new rate");
  yes(taxes.pay(s,wp,35),"manual tax payment");yes(taxes.owed(s,worker)==25,"tax decremented");
  yes(taxes.exempt(s,mayor,worker,true),"tax exempt");
  s.world.day=15*24000L;yes(taxes.owed(s,worker)==25,"tax exemption no accruing");
  city.members.remove(worker);yes(taxes.owed(s,worker)==25,"old tax survives move");
  var taxLoad=KingdomTaxData.class.getDeclaredMethod("load",CompoundTag.class);taxLoad.setAccessible(true);
  var taxReloaded=(KingdomTaxData)taxLoad.invoke(null,taxes.save(new CompoundTag()));
  yes(taxReloaded.owed(s,worker)==25,"tax persistence");
  yes(taxReloaded.pay(s,wp,25),"pay historical tax");
  System.out.println("PASS: stage1 money conservation, firm trade, NPC contracts, corporate/city payroll, tax, migration snapshots");
 }
}