package ru.projectkingdom.administration;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomTaxData;
import ru.projectkingdom.npc.KingdomBankData;
import net.minecraft.network.chat.Component;

public record AdministrativeTextPacket(UUID townId,AdministrativeKind kind,String field,String value) {
    public static String validatedText(String input){
        String clean=input==null?"":input.trim();
        if(clean.isEmpty()||clean.length()>48||clean.indexOf('\u00a7')>=0||clean.chars().anyMatch(Character::isISOControl))return null;
        return clean;
    }
    private static String validatedField(String input){
        if(input==null||input.isBlank()||input.length()>24||!input.matches("[a-z_]+"))return null;return input;
    }
    private static String validatedCommandValue(String input){
        if(input==null||input.isBlank()||input.length()>64||input.indexOf('§')>=0||input.chars().anyMatch(Character::isISOControl))return null;
        return input.trim();
    }
    public static void encode(AdministrativeTextPacket packet,FriendlyByteBuf buffer){
        buffer.writeUUID(packet.townId);buffer.writeUtf(packet.kind==null?"":packet.kind.id(),32);
        buffer.writeUtf(packet.field==null?"":packet.field,32);buffer.writeUtf(packet.value==null?"":packet.value,96);
    }
    public static AdministrativeTextPacket decode(FriendlyByteBuf buffer){
        return new AdministrativeTextPacket(buffer.readUUID(),AdministrativeKind.byId(buffer.readUtf(32)).orElse(null),buffer.readUtf(32),buffer.readUtf(96));
    }
    public static void handle(AdministrativeTextPacket packet,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context context=supplier.get();context.enqueueWork(()->{
            var sender=context.getSender();String field=validatedField(packet.field);
            if(sender==null||packet.kind==null||field==null
                    ||!(sender.containerMenu instanceof AdministrativeCoreMenu menu)||!packet.townId.equals(menu.townId())
                    ||packet.kind!=menu.kind()||!menu.stillValid(sender))return;
            if(field.equals("food_config")||field.equals("food_buy")
                    ||field.equals("food_refresh")){
                var civics=KingdomCivicsData.get(sender.getServer());
                var town=civics.townById(packet.townId);
                if(town==null||!civics.isMember(packet.townId,sender.getUUID())
                        ||(packet.kind!=AdministrativeKind.CENTRAL_WAREHOUSE
                            &&packet.kind!=AdministrativeKind.BANK
                            &&packet.kind!=AdministrativeKind.TOWN_HALL))return;
                String notice="";
                if(field.equals("food_config")){
                    var admin=KingdomAdministrationData.get(sender.getServer());
                    if(!AdministrativePermissionService.mayManage(sender.getUUID(),
                            admin.founder(town.id()),town.mayor(),
                            admin.roles(town.id(),sender.getUUID()),AdministrativeKind.CENTRAL_WAREHOUSE))
                        return;
                    String value=packet.value==null?"":packet.value.trim();
                    if(!value.matches("[0-9]{1,6}"))notice="Численность: от 1 до 100000.";
                    else {
                        int population=Integer.parseInt(value);
                        int days=ru.projectkingdom.npc.KingdomSeasonCalendar.daysInMonth(sender.getServer());
                        if(population<1||population>100000||days<1||days>833)
                            notice="Неверное население или календарь TFC недоступен.";
                        else {
                            var policy=ru.projectkingdom.npc.KingdomMunicipalFoodReserves.Policy.fromTfcMonth(
                                    population,days);
                            ru.projectkingdom.npc.KingdomMunicipalFoodReserves.get(sender.getServer())
                                    .configure(town.id(),policy);
                            notice="План на "+population+" жителей сохранён; запас пересчитан.";
                        }
                    }
                }else if(field.equals("food_buy")){
                    if(packet.kind!=AdministrativeKind.BANK)return;
                    String value=packet.value==null?"":packet.value.trim();
                    int at=value.lastIndexOf('|');
                    if(at<1 || at==value.length()-1)return;
                    String item=value.substring(0,at);
                    String count=value.substring(at+1);
                    if(item.length()>64||!count.matches("[0-9]{1,2}"))return;
                    // The menu is only a hint: actual bank cash, TFC stock,
                    // rights, price and recipient are checked again at purchase.
                    if(menu.foodView().offers().stream().noneMatch(v->v.itemId().equals(item)))
                        return;
                    var status=ru.projectkingdom.npc.KingdomMunicipalProcurement.purchase(
                            sender,item,Integer.parseInt(count));
                    notice=switch(status){
                        case SUCCESS->"Оплачено. NPC-курьер везёт реальные товары.";
                        case NO_PERMISSION->"Недостаточно прав распоряжаться казной.";
                        case NOT_AT_BANK->"Для оплаты откройте банк своего города.";
                        case UNKNOWN_RESERVE->"Склад или календарь TFC не проверены.";
                        case NO_SHORTAGE->"Нет подтверждённой нехватки на этот объём.";
                        case ALREADY_ORDERED->"Заказ уже активен либо ожидает сверки.";
                        case NO_MARKET_FOOD->"Товар, касса или место на складе недоступны.";
                        case NO_DESTINATION->"Не найден действующий городской склад.";
                        case NO_MONEY->"Не хватает обеспеченных монет в банке.";
                        case PAYMENT_REQUIRES_REVIEW->"Платёж на сверке. Повторять нельзя!";
                    };
                }else notice="Остатки и предложения обновлены.";
                var core=sender.serverLevel().getBlockEntity(menu.corePos());
                if(core instanceof AdministrativeCoreBlockEntity active)
                    AdministrativeCoreMenu.open(sender,active,notice);
                return;
            }
            if(field.equals("bank_deposit")||field.equals("bank_withdraw")){
                if(packet.kind!=AdministrativeKind.BANK)return;
                String value=packet.value==null?"":packet.value.trim();
                if(!value.matches("[0-9]{1,7}")){
                    sender.sendSystemMessage(Component.literal("[Банк] Укажите целую сумму от 1 до 1 000 000 медных."));
                    return;
                }
                long amount;
                try{amount=Long.parseLong(value);}catch(NumberFormatException ignored){return;}
                if(amount<1||amount>1_000_000){
                    sender.sendSystemMessage(Component.literal("[Банк] Слишком большая сумма. Максимум 1 000 000 медных за операцию."));
                    return;
                }
                var bank=KingdomBankData.get(sender.getServer());
                if(!packet.townId.equals(bank.bankTown(sender))){
                    sender.sendSystemMessage(Component.literal("[Банк] Откройте банковский блок своего города и встаньте ближе к нему."));
                    return;
                }
                boolean deposit=field.equals("bank_deposit");
                boolean ok=deposit?bank.deposit(sender,amount):bank.withdraw(sender,amount);
                sender.sendSystemMessage(Component.literal(ok
                    ?"[Банк] "+(deposit?"Внесено ":"Снято ")+amount+" медных. Реальные монеты переведены."
                    :"[Банк] "+(deposit?bank.depositProblem(sender,amount):bank.withdrawProblem(sender,amount))));
                if(ok)sender.closeContainer();
                return;
            }
            if(field.equals("pay_taxes")){
                if(packet.kind!=AdministrativeKind.TREASURY)return;
                var taxes=KingdomTaxData.get(sender.getServer());
                long owed=taxes.owedToTown(sender.getServer(),sender.getUUID(),packet.townId);
                String text=packet.value==null?"":packet.value.trim();
                long amount;
                if(text.equals("all"))amount=owed;
                else {
                    if(!text.matches("[0-9]{1,7}")){
                        sender.sendSystemMessage(Component.literal("[Казначейство] Укажите число медных монет от 1 до суммы долга."));
                        return;
                    }
                    try{amount=Long.parseLong(text);}catch(NumberFormatException ex){return;}
                }
                var result=taxes.payAtTreasury(sender,packet.townId,amount);
                sender.sendSystemMessage(Component.literal("[Казначейство] "+KingdomTaxData.paymentMessage(result)));
                if(result==KingdomTaxData.PaymentResult.SUCCESS)sender.closeContainer();
                return;
            }
            if(!menu.manager())return;
            if(field.equals("appoint_role")||field.equals("revoke_role")||field.equals("appoint_mayor")){
                if(menu.kind()!=AdministrativeKind.TOWN_HALL)return;
                String command=validatedCommandValue(packet.value);if(command==null)return;
                KingdomCivicsData civics=KingdomCivicsData.get(sender.getServer());
                var town=civics.townById(packet.townId);if(town==null)return;
                KingdomAdministrationData admin=KingdomAdministrationData.get(sender.getServer());
                // The menu was a server snapshot, but rights and the local residency must
                // be checked anew. A previously valid mayor may have been replaced.
                if(!AdministrativePermissionService.mayManage(sender.getUUID(),admin.founder(town.id()),
                        town.mayor(),admin.roles(town.id(),sender.getUUID()),AdministrativeKind.TOWN_HALL))return;
                UUID target;
                MunicipalRole role;
                try{
                    if(field.equals("appoint_mayor")){
                        target=UUID.fromString(command);role=MunicipalRole.MAYOR;
                    }else{
                        int separator=command.indexOf('|');if(separator<1)return;
                        target=UUID.fromString(command.substring(0,separator));
                        role=MunicipalRole.valueOf(command.substring(separator+1));
                        if(role==MunicipalRole.MAYOR)return;
                    }
                }catch(IllegalArgumentException ignored){return;}
                if(menu.appointmentCandidates().stream().noneMatch(c->c.id().equals(target))
                        ||!KingdomTownHallCandidates.eligible(sender.getServer(),town.id(),target))return;
                // The founder alone can replace the mayor. Do not enroll a candidate
                // as a side effect of a mayor's unauthorized attempt.
                if(role==MunicipalRole.MAYOR && !sender.getUUID().equals(admin.founder(town.id())))return;
                // NPCs proven to live at a claimed city home or assigned to the
                // city may be enrolled when first appointed. Foreign NPCs cannot.
                if(!KingdomTownHallCandidates.enrollLocalNpc(sender.getServer(),town.id(),target)
                        &&!civics.isMember(town.id(),target))return;
                boolean changed;
                if(role==MunicipalRole.MAYOR)
                    changed=civics.replaceMayorAuthorized(packet.townId,sender.getUUID(),target);
                else if(field.equals("appoint_role"))
                    changed=admin.appoint(packet.townId,sender.getUUID(),town.mayor(),target,role);
                else changed=admin.revoke(packet.townId,sender.getUUID(),town.mayor(),target,role);
                sender.sendSystemMessage(Component.literal(changed
                    ?"[Ратуша] Должность обновлена. Откройте ратушу снова для нового списка."
                    :"[Ратуша] Не удалось изменить назначение: проверьте полномочия или прежнюю должность."));
                if(changed){
                    admin.record(packet.townId,sender.getUUID(),role,field,sender.level().getGameTime());
                    sender.closeContainer();
                }
                return;
            }
            String text=validatedText(packet.value);if(text==null)return;
            KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,"text."+field+"="+text,sender.level().getGameTime());
        });context.setPacketHandled(true);
    }
}
