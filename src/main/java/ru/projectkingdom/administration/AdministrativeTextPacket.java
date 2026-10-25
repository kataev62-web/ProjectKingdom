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
            if(field.equals("food_config") || field.equals("food_buy")) {
                if(packet.kind!=AdministrativeKind.CENTRAL_WAREHOUSE
                        && packet.kind!=AdministrativeKind.TOWN_HALL
                        && packet.kind!=AdministrativeKind.BANK)return;
                var civic=KingdomCivicsData.get(sender.getServer());
                var town=civic.townById(packet.townId);
                if(town==null || !civic.isMember(packet.townId,sender.getUUID()))return;
                var admin=KingdomAdministrationData.get(sender.getServer());
                String notice;
                if(field.equals("food_config")) {
                    if(!AdministrativePermissionService.mayManage(sender.getUUID(),
                            admin.founder(town.id()),town.mayor(),
                            admin.roles(town.id(),sender.getUUID()),
                            AdministrativeKind.CENTRAL_WAREHOUSE))return;
                    String value=packet.value==null?"":packet.value.trim();
                    if(!value.matches("[0-9]{1,6}")){
                        notice="Укажите число жителей от 1 до 100000.";
                    }else{
                        int residents=Integer.parseInt(value);
                        int monthDays=ru.projectkingdom.npc.KingdomSeasonCalendar.daysInMonth(sender.getServer());
                        if(residents<1||residents>100000)
                            notice="Допустимо от 1 до 100000 жителей.";
                        else if(monthDays<1||monthDays>833
                                ||ru.projectkingdom.npc.KingdomSeasonCalendar.totalDays(sender.getServer())<0)
                            notice="Календарь TFC недоступен, настройки не изменены.";
                        else {
                            var policy=ru.projectkingdom.npc.KingdomMunicipalFoodReserves.Policy
                                    .fromTfcMonth(residents,monthDays);
                            ru.projectkingdom.npc.KingdomMunicipalFoodReserves.get(sender.getServer())
                                    .configure(town.id(),policy);
                            notice="План резерва сохранён для "+residents+" жителей.";
                        }
                    }
                }else{
                    // Buy actions are allowed ONLY from the real municipal bank
                    // menu, with an additional server-side treasury permission check.
                    if(packet.kind!=AdministrativeKind.BANK
                            ||!AdministrativePermissionService.mayManage(sender.getUUID(),
                                admin.founder(town.id()),town.mayor(),
                                admin.roles(town.id(),sender.getUUID()),
                                AdministrativeKind.TREASURY))return;
                    String value=packet.value==null?"":packet.value.trim();
                    int sep=value.lastIndexOf('|');
                    String item=sep<0?"":value.substring(0,sep);
                    String amount=sep<0?"":value.substring(sep+1);
                    if(!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                            ||!amount.matches("[0-9]{1,2}"))
                        notice="Укажите допустимый товар и количество 1–16.";
                    else {
                        int count=Integer.parseInt(amount);
                        if(count<1||count>16)notice="За один заказ допускается 1–16 предметов.";
                        else {
                            var result=ru.projectkingdom.npc.KingdomMunicipalProcurement.purchase(
                                    sender,item,count);
                            notice=switch(result) {
                                case SUCCESS->"Закупка оплачена, курьер получил заявку.";
                                case NO_PERMISSION->"Недостаточно прав на расход казны.";
                                case NOT_AT_BANK->"Откройте действующий банк своего города.";
                                case UNKNOWN_RESERVE->"Городские запасы не подтверждены: покупка запрещена.";
                                case NO_SHORTAGE->"Подтверждённого дефицита на это количество нет.";
                                case ALREADY_ORDERED->"Товар уже заказан либо оплата требует сверки.";
                                case NO_MARKET_FOOD->"Нет подходящего свежего товара или места на складе.";
                                case NO_DESTINATION->"Не найден городской склад получателя.";
                                case NO_MONEY->"Недостаточно физических монет или не покрыты вклады.";
                                case PAYMENT_REQUIRES_REVIEW->"ОПЛАТА НА СВЕРКЕ — повторять заказ нельзя.";
                            };
                        }
                    }
                }
                if(sender.level().getBlockEntity(menu.corePos())
                        instanceof AdministrativeCoreBlockEntity core)
                    AdministrativeCoreMenu.open(sender,core,notice);
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
                KingdomCivicsData civics=KingdomCivicsData.get(sender.getServer());var town=civics.townById(packet.townId);if(town==null)return;
                boolean changed=false;
                if(field.equals("appoint_mayor")){
                    try{changed=civics.replaceMayorAuthorized(packet.townId,sender.getUUID(),UUID.fromString(command));}catch(IllegalArgumentException ignored){}
                }else{
                    int separator=command.indexOf('|');if(separator<1)return;
                    try{
                        UUID target=UUID.fromString(command.substring(0,separator));MunicipalRole role=MunicipalRole.valueOf(command.substring(separator+1));
                        if(role==MunicipalRole.MAYOR)return;
                        KingdomAdministrationData data=KingdomAdministrationData.get(sender.getServer());
                        if(field.equals("appoint_role")&&civics.isMember(packet.townId,target))changed=data.appoint(packet.townId,sender.getUUID(),town.mayor(),target,role);
                        else if(field.equals("revoke_role"))changed=data.revoke(packet.townId,sender.getUUID(),town.mayor(),target,role);
                    }catch(IllegalArgumentException ignored){}
                }
                if(changed)KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,
                        field,sender.level().getGameTime());
                return;
            }
            String text=validatedText(packet.value);if(text==null)return;
            KingdomAdministrationData.get(sender.getServer()).record(packet.townId,sender.getUUID(),null,"text."+field+"="+text,sender.level().getGameTime());
        });context.setPacketHandled(true);
    }
}
