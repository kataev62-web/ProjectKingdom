package ru.projectkingdom.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** OP4-only recovery. No automatic wallet/coin minting or manual bypass of witness checks. */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BankRecoveryCommands {
    private static final Map<UUID,Pending> CONFIRM=new HashMap<>();
    private record Pending(UUID operator,long expires) {}
    private BankRecoveryCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kingdom-bank-recovery")
            .requires(source->source.hasPermission(4))
            .then(Commands.literal("list").executes(ctx->{
                var ledger=BankOperations.journal(ctx.getSource().getServer());
                if(ledger==null||!ledger.healthy()){
                    say(ctx.getSource(),"Банковский журнал повреждён или недоступен. Все операции закрыты.");
                    return 0;
                }
                var pending=ledger.unresolved();
                if(pending.isEmpty()){say(ctx.getSource(),"Нет незавершённых банковских операций.");return 1;}
                int count=0;
                for(var entry:pending){
                    say(ctx.getSource(),entry.id()+" | "+entry.kind()+" | город "+entry.town()
                            +" | "+entry.amount()+" мед. | "+entry.state());
                    if(++count>=20)break;
                }
                say(ctx.getSource(),"Показано "+count+" из "+pending.size()+". Сначала сверяй NBT, сейф, кошелёк и городские счета.");
                return count;
            }))
            .then(Commands.literal("resolve")
                .then(Commands.argument("operation",StringArgumentType.word())
                .executes(ctx->{
                    if(!(ctx.getSource().getEntity() instanceof ServerPlayer operator)){
                        say(ctx.getSource(),"Для сверки необходим OP4 непосредственно у банка города.");
                        return 0;
                    }
                    UUID id;
                    try{id=UUID.fromString(StringArgumentType.getString(ctx,"operation"));}
                    catch(IllegalArgumentException error){say(ctx.getSource(),"Некорректный UUID операции.");return 0;}
                    var ledger=BankOperations.journal(operator.getServer());
                    var entry=ledger==null?null:ledger.find(id);
                    if(entry==null||!ledger.unresolved().stream().anyMatch(e->e.id().equals(id))){
                        say(ctx.getSource(),"Операция не найдена или уже закрыта.");return 0;
                    }
                    long now=operator.serverLevel().getGameTime();
                    Pending pending=CONFIRM.get(id);
                    if(pending==null||!pending.operator().equals(operator.getUUID())
                            ||pending.expires()<now||pending.expires()>now+600){
                        CONFIRM.put(id,new Pending(operator.getUUID(),now+600));
                        say(ctx.getSource(),"Проверь сохранённые монеты, NBT, личные счета и казну. Повтори команду в течение 30 секунд для проверки точного снимка. Монеты НЕ выдаются.");
                        return 0;
                    }
                    CONFIRM.remove(id);
                    if(!BankOperations.reconcile(operator,id)){
                        say(ctx.getSource(),"Снимки не совпадают или игрок отсутствует. Карантин сохранён; нужна ручная сверка копии мира.");
                        return 0;
                    }
                    say(ctx.getSource(),"Квитанция сверки записана. Физические монеты и счета не изменялись.");
                    return 1;
                }))));
    }
    private static void say(net.minecraft.commands.CommandSourceStack source,String text) {
        source.sendSuccess(()->Component.literal("[Банк Kingdom] "+text),false);
    }
}
