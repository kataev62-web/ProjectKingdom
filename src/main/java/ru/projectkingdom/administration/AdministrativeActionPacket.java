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
    public static final int JOIN_TOWN=100,PAY_RESERVED_LAND=204,BIND_HALL_CASHBOX=115;
    public static final int BIND_TREASURY=110,BIND_BANK=111,BIND_WAREHOUSE=112,BIND_MARKET=113,BIND_PRISON_CELL=114;
    public static final int BANK_DEPOSIT_100=200,BANK_WITHDRAW_100=201,TREASURY_DEPOSIT_100=202,TREASURY_WITHDRAW_100=203;
    // 204 is reserved for public land payment; social actions use distinct 220+ ids.
    public static final int SOCIAL_PROMOTE=220;
    public static final int PETITION_ROADS=221,PETITION_MARKET=222,PETITION_SAFETY=223;
    public static final int PETITION_ACCEPT=224,PETITION_REJECT=225;
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
                    case BOUGHT -> "Участок приобретён; монеты внесены в городскую кассу.";
                    case NOT_RESERVED -> "У вас нет действующей брони в этой ратуше.";
                    case WRONG_TOWN -> "Обратитесь в ратушу города-владельца земли.";
                    case EXPIRED -> "Срок бронирования истёк, оформите новое через карту.";
                    case CHANGED_OFFER -> "Цена или владелец участка изменились; оплату отменено.";
                    case NO_VAULT -> "Городская касса не привязана или не загружена.";
                    case NO_CASH_OR_ROOM -> "Недостаточно монет или касса переполнена.";
                    case NEEDS_REVIEW -> "Оплата требует сверки администрацией; повторять нельзя.";
                };
                sender.sendSystemMessage(Component.literal("[Ратуша] "+info));
                if(result==KingdomLandReservations.Result.BOUGHT)sender.closeContainer();
                return;
            }
            boolean publicFinance=(packet.kind==AdministrativeKind.BANK&&(packet.action==BANK_DEPOSIT_100||packet.action==BANK_WITHDRAW_100))
                    ||(packet.kind==AdministrativeKind.TREASURY&&packet.action==TREASURY_DEPOSIT_100);
            boolean townHall=packet.kind==AdministrativeKind.TOWN_HALL;
            boolean promote=townHall&&packet.action==SOCIAL_PROMOTE;
            boolean petitionFile=townHall&&packet.action>=PETITION_ROADS&&packet.action<=PETITION_SAFETY;
            boolean petitionReview=townHall&&(packet.action==PETITION_ACCEPT||packet.action==PETITION_REJECT);
            if(!publicFinance&&!promote&&!petitionFile&&!petitionReview&&!menu.manager())return;
            if(promote){
                // Bind the click to the server-authored rank in this menu; stale packets cannot buy a second level.
                if(ru.projectkingdom.social.KingdomSocialData.get(sender.getServer())
                        .rank(sender.getUUID()).level()!=menu.social().level())return;
                boolean success=ru.projectkingdom.social.KingdomSocialPromotionService.promote(sender,packet.townId);
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(success
                    ?"[Project Kingdom] Социальный статус повышен. Откройте ратушу заново."
                    :"[Project Kingdom] Повышение недоступно: проверьте заслуги, задолженности и настоящую кассу."));
                if(success)sender.closeContainer();
                return;
            }
            if(petitionFile){
                var topic=switch(packet.action){
                    case PETITION_ROADS->ru.projectkingdom.social.KingdomCitizenPetitionsData.Topic.ROADS;
                    case PETITION_MARKET->ru.projectkingdom.social.KingdomCitizenPetitionsData.Topic.MARKET;
                    case PETITION_SAFETY->ru.projectkingdom.social.KingdomCitizenPetitionsData.Topic.SAFETY;
                    default->null;
                };
                boolean success=ru.projectkingdom.social.KingdomCivicService.file(sender,packet.townId,topic);
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(success
                    ?"[Project Kingdom] Ваше обращение принято."
                    :"[Project Kingdom] Обращение недоступно: статус 3+, репутация 30+, сроки и лимиты."));
                if(success)sender.closeContainer();
                return;
            }
            if(petitionReview){
                boolean success=ru.projectkingdom.social.KingdomCivicService.review(
                    sender,packet.townId,menu.civic().pendingId(),packet.action==PETITION_ACCEPT);
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(success
                    ?"[Project Kingdom] Решение сохранено."
                    :"[Project Kingdom] Обращение уже рассмотрено или нет полномочий."));
                if(success)sender.closeContainer();
                return;
            }
            if(packet.action==BANK_DEPOSIT_100){KingdomBankData.get(sender.getServer()).deposit(sender,100);return;}
            if(packet.action==BANK_WITHDRAW_100){KingdomBankData.get(sender.getServer()).withdraw(sender,100);return;}
            if(packet.action==TREASURY_DEPOSIT_100){
                var civics=KingdomCivicsData.get(sender.getServer());
                var vault=KingdomPhysicalCash.townVault(sender,packet.townId,AdministrativeBindingRole.TREASURY_VAULT);
                if(vault!=null && civics.isMember(packet.townId,sender.getUUID())
                        &&KingdomPhysicalCash.deposit(sender,vault,100)){
                    if(!civics.depositTownTo(packet.townId,sender.getUUID(),100)){
                        if(!KingdomPhysicalCash.withdraw(sender,vault,100))
                            throw new IllegalStateException("Treasury cash compensation failed");
                    }
                }return;
            }
            if(packet.action==TREASURY_WITHDRAW_100){
                KingdomCivicsData civics=KingdomCivicsData.get(sender.getServer());
                var admin=KingdomAdministrationData.get(sender.getServer());
                var vault=KingdomPhysicalCash.townVault(sender,packet.townId,AdministrativeBindingRole.TREASURY_VAULT);
                if(vault!=null && admin.treasuryReserve(packet.townId)>=100
                        && KingdomPhysicalCash.value(vault)>=admin.treasuryReserve(packet.townId)
                        &&KingdomPhysicalCash.withdraw(sender,vault,100)){
                    if(!civics.withdrawTownAuthorized(packet.townId,sender.getUUID(),100)){
                        if(!KingdomPhysicalCash.deposit(sender,vault,100))
                            throw new IllegalStateException("Treasury cash compensation failed");
                    }
                }return;
            }
            AdministrativeBindingRole role=switch(packet.action){
                case BIND_TREASURY,BIND_HALL_CASHBOX->AdministrativeBindingRole.TREASURY_VAULT;
                case BIND_BANK->AdministrativeBindingRole.BANK_VAULT;
                case BIND_WAREHOUSE->AdministrativeBindingRole.WAREHOUSE_STORAGE;
                case BIND_MARKET->AdministrativeBindingRole.MARKET_STORAGE;
                case BIND_PRISON_CELL->AdministrativeBindingRole.PRISON_CELL;
                default->null;
            };
            if(role!=null)AdministrativeAccessService.begin(sender,packet.townId,packet.kind,role);
        });context.setPacketHandled(true);
    }
}
