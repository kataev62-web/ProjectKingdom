package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Taxes are assessed as debt; NEVER debit any wallet automatically. */
public final class KingdomTaxData extends SavedData {
    private static final String KEY="kingdom_taxes_v1";
    public record Debt(UUID town,UUID person,long amount,long lastDay) {}
    public record Rate(long effectiveDay,long copper) {}
    private final Map<String,Debt> debts=new LinkedHashMap<>();
    private final Map<UUID,List<Rate>> rates=new LinkedHashMap<>();
    private final Map<UUID,Set<UUID>> exemptions=new LinkedHashMap<>();
    private static String key(UUID town,UUID player){return town+"|"+player;}
    public static KingdomTaxData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(KingdomTaxData::load,KingdomTaxData::new,KEY);}
    public long rate(UUID town,long day){
        long value=10;
        for(Rate r:rates.getOrDefault(town,List.of()))if(r.effectiveDay()<=day)value=r.copper();
        return value;
    }
    /** New tax rate takes effect next game day: past days never retroactively change. */
    public boolean setRate(MinecraftServer server,UUID mayor,long copper){
        var town=KingdomCivicsData.get(server).townOf(mayor);
        if(town==null || !town.mayor().equals(mayor) || copper<0 || copper>1000)return false;
        long effective=server.overworld().getDayTime()/24000L+1;
        List<Rate> history=rates.computeIfAbsent(town.id(),k->new ArrayList<>());
        history.removeIf(r->r.effectiveDay()==effective);
        history.add(new Rate(effective,copper));history.sort(Comparator.comparingLong(Rate::effectiveDay));setDirty();return true;
    }
    public boolean isExempt(UUID town,UUID resident) {
        return exemptions.getOrDefault(town,Set.of()).contains(resident);
    }
    /** Only current residents may be exempted; old debt remains payable. */
    public boolean exempt(MinecraftServer server,UUID mayor,UUID resident,boolean enable){
        var civics=KingdomCivicsData.get(server);var town=civics.townOf(mayor);
        if(town==null || !town.mayor().equals(mayor) || resident==null)return false;
        var target=civics.townOf(resident);
        if(target==null || !target.id().equals(town.id()))return false;
        // Settle all obligations earned so far before toggling the future exemption.
        owed(server,resident);
        Set<UUID> people=exemptions.computeIfAbsent(town.id(),k->new LinkedHashSet<>());
        if(enable)people.add(resident);else people.remove(resident);
        setDirty();return true;
    }
    public long owed(MinecraftServer server,UUID player){
        var town=KingdomCivicsData.get(server).townOf(player);
        if(town!=null)assess(server,player,town.id());
        long total=0;
        for(Debt d:debts.values())if(d.person().equals(player))
            total=total>Long.MAX_VALUE-d.amount()?Long.MAX_VALUE:total+d.amount();
        return total;
    }
    public Collection<Debt> unpaid(UUID player){
        List<Debt> result=new ArrayList<>();
        for(Debt d:debts.values())if(d.person().equals(player)&&d.amount()>0)result.add(d);
        return Collections.unmodifiableList(result);
    }
    /** Called at login / once per in-game day by scheduler for existing residents. */
    public void assess(MinecraftServer server,UUID player,UUID townId){
        long day=Math.max(0,server.overworld().getDayTime()/24000L);
        String id=key(townId,player);
        Debt prev=debts.get(id);
        if(prev==null){debts.put(id,new Debt(townId,player,0,day));setDirty();return;}
        if(day<=prev.lastDay())return;
        long add=0;
        if(!exemptions.getOrDefault(townId,Set.of()).contains(player)){
            // Split by change points, not 1 loop per day (years of offline players).
            long start=prev.lastDay();
            for(Rate event:rates.getOrDefault(townId,List.of())){
                if(event.effectiveDay()<=start || event.effectiveDay()>=day)continue;
                add=saturatingAdd(add,saturatingMultiply(event.effectiveDay()-start,rate(townId,start)));
                start=event.effectiveDay();
            }
            add=saturatingAdd(add,saturatingMultiply(day-start,rate(townId,start)));
        }
        debts.put(id,new Debt(townId,player,saturatingAdd(prev.amount(),add),day));setDirty();
    }
    private static long saturatingMultiply(long a,long b){return a>0&&b>Long.MAX_VALUE/a?Long.MAX_VALUE:a*b;}
    private static long saturatingAdd(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    /** Current local debt only; a former city's debt stays with its original creditor. */
    public long owedToTown(MinecraftServer server,UUID player,UUID town){
        if(server==null||player==null||town==null)return 0;
        var current=KingdomCivicsData.get(server).townOf(player);
        if(current!=null && current.id().equals(town))assess(server,player,town);
        Debt entry=debts.get(key(town,player));
        return entry==null?0:Math.max(0,entry.amount());
    }

    public enum PaymentResult {
        SUCCESS, NO_DEBT, INVALID_AMOUNT, NOT_ENOUGH_COINS, BANK_NOT_CONNECTED,
        LEGACY_RESERVE_MISMATCH, BANK_FULL, TREASURY_LIMIT, PAYMENT_REVIEW_REQUIRED
    }
    /** Treasury counter collects a specified tax amount; only the bank owns coins.
     * In particular, a tax payment never credits a personal bank account.
     * A municipal ledger receipt is booked only AFTER physical coin insertion.
     */
    public PaymentResult payAtTreasury(ServerPlayer player,UUID townId,long requested){
        if(player==null||townId==null||requested<1||requested>1_000_000)
            return PaymentResult.INVALID_AMOUNT;
        MinecraftServer server=player.getServer();
        long debt=owedToTown(server,player.getUUID(),townId);
        if(debt<1)return PaymentResult.NO_DEBT;
        if(requested>debt)return PaymentResult.INVALID_AMOUNT;
        if(KingdomCurrency.balance(player)<requested)return PaymentResult.NOT_ENOUGH_COINS;
        var civics=KingdomCivicsData.get(server);
        var town=civics.townById(townId);
        if(town==null||town.treasury()>Long.MAX_VALUE-requested)return PaymentResult.TREASURY_LIMIT;
        var admin=ru.projectkingdom.administration.KingdomAdministrationData.get(server);
        var vault=KingdomPhysicalCash.townVault(player,townId,
            ru.projectkingdom.administration.AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return PaymentResult.BANK_NOT_CONNECTED;
        long backing=KingdomPhysicalCash.value(vault),bankClaims=admin.bankReserve(townId),
            townClaims=admin.treasuryReserve(townId);
        if(bankClaims>Long.MAX_VALUE-townClaims||backing<bankClaims+townClaims)
            return PaymentResult.LEGACY_RESERVE_MISMATCH;
        if(townClaims>Long.MAX_VALUE-requested)return PaymentResult.TREASURY_LIMIT;
        if(!KingdomPhysicalCash.deposit(player,vault,requested))return PaymentResult.BANK_FULL;
        if(!civics.depositVerifiedTax(townId,requested)){
            // No debt mutation occurred. Refund the exact physical value.
            // Fail closed for operator review only if the refund itself fails.
            return KingdomPhysicalCash.withdraw(player,vault,requested)
                ?PaymentResult.TREASURY_LIMIT:PaymentResult.PAYMENT_REVIEW_REQUIRED;
        }
        Debt previous=debts.get(key(townId,player.getUUID()));
        if(previous==null||previous.amount()<requested)return PaymentResult.PAYMENT_REVIEW_REQUIRED;
        debts.put(key(townId,player.getUUID()),new Debt(townId,player.getUUID(),
                previous.amount()-requested,previous.lastDay()));
        setDirty();
        return PaymentResult.SUCCESS;
    }
    public static String paymentMessage(PaymentResult result){
        return switch(result){
            case SUCCESS->"Оплата прошла. Настоящие монеты зачислены в банковский сейф города.";
            case NO_DEBT->"Перед этим городом у вас нет задолженности.";
            case INVALID_AMOUNT->"Введите сумму от 1 медной монеты до размера своего долга.";
            case NOT_ENOUGH_COINS->"Недостаточно монет в кошельке. Положите монеты в кошелёк Kingdom.";
            case BANK_NOT_CONNECTED->"В городе не привязан банковский сейф или его чанк не загружен. Обратитесь в банк.";
            case LEGACY_RESERVE_MISMATCH->"В банке недостаточно физических монет для уже записанных счетов и казны. Нужна сверка старого резерва.";
            case BANK_FULL->"В банковском сейфе нет места для монет или хранилище не принимает их.";
            case TREASURY_LIMIT->"Городской счёт сейчас не может принять эту сумму.";
            case PAYMENT_REVIEW_REQUIRED->"Платёж нуждается в проверке администратора. Повторно не оплачивайте.";
        };
    }
    /** Legacy entrypoint now also uses the physical bank vault; no virtual tax minting. */
    public boolean pay(MinecraftServer server,ServerPlayer player,long requested){
        if(player==null||!KingdomBankData.get(server).atBranch(player))return false;
        var town=KingdomCivicsData.get(server).townOf(player.getUUID());
        return town!=null&&payAtTreasury(player,town.id(),requested)==PaymentResult.SUCCESS;
    }
    private static KingdomTaxData load(CompoundTag root){
        KingdomTaxData data=new KingdomTaxData();
        for(Tag tag:root.getList("Debts",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)tag;
            if(!t.hasUUID("Town") || !t.hasUUID("Person"))continue;
            Debt d=new Debt(t.getUUID("Town"),t.getUUID("Person"),Math.max(0,t.getLong("Amount")),Math.max(0,t.getLong("LastDay")));
            data.debts.put(key(d.town(),d.person()),d);
        }
        for(Tag tag:root.getList("Rates",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)tag;
            if(t.hasUUID("Town") && t.getLong("Value")>=0 && t.getLong("Value")<=1000)
                data.rates.computeIfAbsent(t.getUUID("Town"),k->new ArrayList<>()).add(new Rate(Math.max(0,t.getLong("Day")),t.getLong("Value")));
        }
        for(List<Rate> list:data.rates.values())list.sort(Comparator.comparingLong(Rate::effectiveDay));
        for(Tag tag:root.getList("Exemptions",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)tag;
            if(t.hasUUID("Town") && t.hasUUID("Person"))data.exemptions.computeIfAbsent(t.getUUID("Town"),k->new LinkedHashSet<>()).add(t.getUUID("Person"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag a=new ListTag();for(Debt d:debts.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Town",d.town());t.putUUID("Person",d.person());
            t.putLong("Amount",d.amount());t.putLong("LastDay",d.lastDay());a.add(t);
        }root.put("Debts",a);
        a=new ListTag();for(var e:rates.entrySet())for(Rate r:e.getValue()){
            CompoundTag t=new CompoundTag();t.putUUID("Town",e.getKey());t.putLong("Day",r.effectiveDay());t.putLong("Value",r.copper());a.add(t);
        }root.put("Rates",a);
        a=new ListTag();for(var e:exemptions.entrySet())for(UUID p:e.getValue()){
            CompoundTag t=new CompoundTag();t.putUUID("Town",e.getKey());t.putUUID("Person",p);a.add(t);
        }root.put("Exemptions",a);return root;
    }
}
