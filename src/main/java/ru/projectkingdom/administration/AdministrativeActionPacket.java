package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomBankData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomCurrency;
import ru.projectkingdom.npc.KingdomPhysicalCash;
import ru.projectkingdom.npc.KingdomLandReservations;

public record AdministrativeActionPacket(UUID townId,AdministrativeKind kind,int action) {
    public static final int JOIN_TOWN=100,PAY_RESERVED_LAND=204,BIND_HALL_CASHBOX=115,CONSOLIDATE_BANK=205,RECONCILE_BANK=206;
    public static final int BIND_TREASURY=110,BIND_BANK=111,BIND_WAREHOUSE=112,BIND_MARKET=113,BIND_PRISON_CELL=114;
    public static final int BANK_DEPOSIT_100=200,BANK_WITHDRAW_100=201,TREASURY_DEPOSIT_100=202,TREASURY_WITHDRAW_100=203;
    public static void encode(AdministrativeActionPacket packet,FriendlyByteBuf buffer){
        buffer.writeUUID(packet.townId);buffer.writeUtf(packet.kind==null?"":packet.kind.id(),32);buffer.writeInt(packet.action);
    }
    public static AdministrativeActionPacket decode(FriendlyByteBuf buffer){
        return new AdministrativeActionPacket(buffer.readUUID(),AdministrativeKind.byId(buffer.readUtf(32)).orElse(null),buffer.readInt());
    }
    public static void handle(AdministrativeActionPacket packet,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context context=supplier.get();context.enqueueWork(()->{
            var sender=context.getSender();
            if(sender==null||packet.kind==null||!(sender.containerMenu instanceof AdministrativeCoreMenu menu)
                    ||!packet.townId.equals(menu.townId())||packet.kind!=menu.kind()||!menu.stillValid(sender))return;
            if(packet.action==JOIN_TOWN){
                // Joining is the ONLY public mutation through a town hall.
                // The sender and target town are never supplied by the client.
                if(packet.kind!=AdministrativeKind.TOWN_HALL)return;
                var civics=KingdomCivicsData.get(sender.getServer());
                var town=civics.townById(menu.townId());
                if(town==null || civics.townOf(sender.getUUID())!=null)return;
                if(civics.joinTown(sender.getUUID(),town.id())){
                    sender.sendSystemMessage(Component.literal("Вы вступили в город «"+town.name()+"»!"));
                    sender.closeContainer(); // Force a fresh permission snapshot on next opening.
                }
                return;
            }
            if(packet.action==PAY_RESERVED_LAND) {
                if(packet.kind!=AdministrativeKind.TOWN_HALL)return;
                var result=KingdomLandReservations.get(sender.getServer()).payAtHall(sender,packet.townId);
                String info=switch(result) {
                    case BOUGHT -> "Участок приобретён; монеты поступили в городской банк.";
                    case NOT_RESERVED -> "У вас нет действующей брони в этой ратуше.";
                    case WRONG_TOWN -> "Обратитесь в ратушу города-владельца земли.";
                    case EXPIRED -> "Срок бронирования истёк, оформите новое через карту.";
                    case CHANGED_OFFER -> "Цена или владелец участка изменились; оплата отменена.";
                    case NO_VAULT -> "Банковский сейф города не привязан или не загружен.";
                    case NO_CASH_OR_ROOM -> "Недостаточно монет или касса переполнена.";
                    case NEEDS_REVIEW -> "Оплата требует сверки администрацией; повторять нельзя.";
                };
                sender.sendSystemMessage(Component.literal("[Ратуша] "+info));
                if(result==KingdomLandReservations.Result.BOUGHT)sender.closeContainer();
                return;
            }
            // All municipal cash is kept in the bank. Legacy cashbox actions
            // are rejected even if an old client sends an explicit packet.
            if(packet.action==BIND_HALL_CASHBOX||packet.action==BIND_TREASURY
                    ||packet.action==TREASURY_DEPOSIT_100||packet.action==TREASURY_WITHDRAW_100){
                sender.sendSystemMessage(Component.literal("[Казначейство] Отдельная касса упразднена. Налоги и сборы поступают в городской банк."));
                return;
            }
            boolean publicFinance=packet.kind==AdministrativeKind.BANK
                    &&(packet.action==BANK_DEPOSIT_100||packet.action==BANK_WITHDRAW_100);
            if(!publicFinance&&!menu.manager())return;
            if(packet.action==BANK_DEPOSIT_100){
                var bank=KingdomBankData.get(sender.getServer());
                boolean ok=bank.deposit(sender,100);
                sender.sendSystemMessage(Component.literal(ok
                    ?"[Банк] Внесено 100 медных: реальные монеты помещены в сейф."
                    :"[Банк] "+bank.depositProblem(sender,100)));
                return;
            }
            if(packet.action==BANK_WITHDRAW_100){
                var bank=KingdomBankData.get(sender.getServer());
                boolean ok=bank.withdraw(sender,100);
                sender.sendSystemMessage(Component.literal(ok
                    ?"[Банк] Выдано 100 медных из реального банковского сейфа."
                    :"[Банк] "+bank.withdrawProblem(sender,100)));
                return;
            }
            if(packet.action==CONSOLIDATE_BANK){
                if(packet.kind!=AdministrativeKind.BANK||!menu.manager())return;
                var bank=KingdomBankData.get(sender.getServer());
                if(!packet.townId.equals(bank.bankTown(sender)))return;
                var result=bank.consolidateOldTownVault(sender);
                sender.sendSystemMessage(Component.literal("[Банк] "+KingdomBankData.legacyTransferMessage(result)));
                if(result==KingdomBankData.LegacyTransferStatus.MOVED)sender.closeContainer();
                return;
            }
            if(packet.action==RECONCILE_BANK){
                if(packet.kind!=AdministrativeKind.BANK||!menu.manager())return;
                var bank=KingdomBankData.get(sender.getServer());
                if(!packet.townId.equals(bank.bankTown(sender)))return;
                var status=bank.reconcileEmptyBank(sender);
                sender.sendSystemMessage(Component.literal("[Банк] "+KingdomBankData.reconciliationMessage(status)));
                if(status==KingdomBankData.ReconcileStatus.DONE)sender.closeContainer();
                return;
            }
            AdministrativeBindingRole role=switch(packet.action){
                case BIND_BANK->AdministrativeBindingRole.BANK_VAULT;
                case BIND_WAREHOUSE->AdministrativeBindingRole.WAREHOUSE_STORAGE;
                case BIND_MARKET->AdministrativeBindingRole.MARKET_STORAGE;
                case BIND_PRISON_CELL->AdministrativeBindingRole.PRISON_CELL;
                default->null;
            };
            if(role!=null){
                boolean ok=AdministrativeAccessService.begin(sender,packet.townId,packet.kind,role);
                sender.sendSystemMessage(Component.literal(ok
                    ?"[Город] "+AdministrativeAccessService.pendingRole(sender.getUUID())
                        +": подойдите не дальше 6 блоков и нажмите ПКМ по настоящему сундуку или складу Kingdom. Рука должна быть пустой."
                    :"[Город] Нет прав на привязку. Откройте нужную службу её управляющим и попробуйте снова."));
            }
        });context.setPacketHandled(true);
    }
}
