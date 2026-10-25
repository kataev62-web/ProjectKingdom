package ru.projectkingdom.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Temporary authenticated administrator entry point. The Kingdom municipal
 * menu can call the same saved policy service later; no client-supplied town ID
 * and no commands that create items or debit the city treasury.
 */
public final class KingdomMunicipalFoodCommands {
    private KingdomMunicipalFoodCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("reserve")
            .then(Commands.literal("status").executes(ctx -> {
                ServerPlayer player=ctx.getSource().getPlayerOrException();
                var town=KingdomCivicsData.get(player.getServer()).townOf(player.getUUID());
                if(town==null){
                    message(player,"Вы не состоите в поселении.");return 0;
                }
                var store=KingdomMunicipalFoodReserves.get(player.getServer());
                var policy=store.policy(town.id());
                if(policy==null) {
                    message(player,"План городского продовольствия ещё не настроен.");return 0;
                }
                var result=store.evaluate(player.getServer(),town.id());
                if(result==null)return 0;
                message(player,"Продовольствие города "+town.name()+
                    " | день TFC "+(result.day()<0?"неизвестен":result.day())+
                    " | население по плану "+policy.residents()+
                    " | горизонт "+result.daysToCover()+" дней.");
                if(!result.complete()) {
                    message(player,"НЕТ ДОСТОВЕРНОГО ОСТАТКА: TFC, городской склад или один из контейнеров недоступен. Новые закупки запрещены.");
                    return 0;
                }
                message(player,"Реальные съедобные порции "+result.edible()+
                    " | испорчено "+result.rotten()+
                    " | требуется "+result.target()+
                    " | нехватка "+result.missing()+
                    " | проверено складов "+result.containers()+".");
                message(player,"Пищевые группы (зерно/фрукты/овощи/белки/молочное): "+result.groups());
                if(result.shortage())
                    message(player,"НЕОБХОДИМО ПОПОЛНЕНИЕ: информационная потребность, товары и деньги НЕ списаны.");
                return 1;
            }))
            .then(Commands.literal("offers")
                .then(Commands.argument("item",StringArgumentType.word())
                .executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var town=KingdomCivicsData.get(player.getServer()).townOf(player.getUUID());
                    if(town==null){message(player,"Для поиска закупок вступите в поселение.");return 0;}
                    String item=StringArgumentType.getString(ctx,"item");
                    var available=KingdomMunicipalProcurement.select(
                            player.serverLevel(),town.id(),item,1,player.getUUID());
                    if(available==null){message(player,"Нет доступного продукта на рынке или нет места в городском резерве.");return 0;}
                    message(player,"Самое дешёвое проверенное предложение: "+item+
                            " — "+available.seller().copperPerItem()+" мед./шт., склад "+
                            available.destination().toShortString()+". Закупка: /knpc reserve buy "+
                            item+" 1 (возле городского банка).");
                    return 1;
                })))
            .then(Commands.literal("buy")
                .then(Commands.argument("item",StringArgumentType.word())
                .then(Commands.argument("count",IntegerArgumentType.integer(1,16))
                .executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    String item=StringArgumentType.getString(ctx,"item");
                    int amount=IntegerArgumentType.getInteger(ctx,"count");
                    var result=KingdomMunicipalProcurement.purchase(player,item,amount);
                    message(player,switch(result){
                        case SUCCESS -> "Городская закупка оплачена реальными монетами. Курьер получит заказ; поступление ещё не подтверждено.";
                        case NO_PERMISSION -> "Нет прав распоряжаться городской казной.";
                        case NOT_AT_BANK -> "Подойдите к действующему банку своего города.";
                        case UNKNOWN_RESERVE -> "Склад/календарь TFC недоступен: закупки заблокированы.";
                        case NO_SHORTAGE -> "Нет подтверждённого дефицита на указанное количество.";
                        case ALREADY_ORDERED -> "Такой товар уже заказан или предыдущая оплата требует проверки.";
                        case NO_MARKET_FOOD -> "Нет подходящего свежего товара, места, кассы продавца или подтверждённого предложения.";
                        case NO_DESTINATION -> "Нет действующего городского склада для приёмки.";
                        case NO_MONEY -> "Недостаточно подтверждённых реальных монет в городском банке.";
                        case PAYMENT_REQUIRES_REVIEW -> "ОПЕРАЦИЯ ОСТАНОВЛЕНА: ручная сверка банковских монет, казны и заказов обязательна. Не повторяйте!";
                    });
                    return result==KingdomMunicipalProcurement.Result.SUCCESS?1:0;
                }))))
            .then(Commands.literal("configure")
                .then(Commands.argument("residents",IntegerArgumentType.integer(1,100000))
                .executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var town=KingdomCivicsData.get(player.getServer()).townOf(player.getUUID());
                    if(town==null || (!player.hasPermissions(2)
                            && !town.mayor().equals(player.getUUID()))) {
                        message(player,"Настройка доступна только главе своего поселения.");return 0;
                    }
                    int monthDays=KingdomSeasonCalendar.daysInMonth(player.getServer());
                    if(monthDays<1 || monthDays>833
                            || KingdomSeasonCalendar.totalDays(player.getServer())<0) {
                        message(player,"Календарь TFC недоступен. Настройка не записана.");return 0;
                    }
                    int residents=IntegerArgumentType.getInteger(ctx,"residents");
                    var policy=KingdomMunicipalFoodReserves.Policy.fromTfcMonth(residents,monthDays);
                    KingdomMunicipalFoodReserves.get(player.getServer()).configure(town.id(),policy);
                    message(player,"Резервы настроены для "+residents+" жителей: "+
                            "календарь TFC "+monthDays+" дней/месяц, зимнее снабжение и "+
                            "страховой запас включены. Денег и товаров не списано.");
                    return 1;
                })));

    }

    private static void message(ServerPlayer player,String line) {
        player.sendSystemMessage(Component.literal("[Резервы Kingdom] "+line));
    }
}
