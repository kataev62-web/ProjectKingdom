package ru.projectkingdom.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import java.util.*;

/** Registers new systems under /kingdom, without modifying legacy /knpc commands. */
public final class KingdomExpansionCommands {
    private KingdomExpansionCommands() {}
    private static void say(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal("[Project Kingdom] "+text),false);}
    public static void register(RegisterCommandsEvent e) {
        var root=Commands.literal("kingdom");
        root.then(KingdomServerAdminCommands.root());
        root.then(Commands.literal("architect")
            .then(Commands.literal("design")
                .then(Commands.argument("width",IntegerArgumentType.integer(7,31))
                .then(Commands.argument("length",IntegerArgumentType.integer(7,31))
                .then(Commands.argument("floors",IntegerArgumentType.integer(1,3)).executes(c->{
                    int w=IntegerArgumentType.getInteger(c,"width"),l=IntegerArgumentType.getInteger(c,"length"),f=IntegerArgumentType.getInteger(c,"floors");
                    var d=KingdomDesignEngine.medievalHouse("medieval_house",w,l,f);
                    say(c.getSource(),"Проект "+w+"x"+l+", этажей "+f+", блоков "+d.blocks().size()+", смета: "+d.bill());return 1;
                })))))
            .then(Commands.literal("place")
                .then(Commands.argument("width",IntegerArgumentType.integer(7,31))
                .then(Commands.argument("length",IntegerArgumentType.integer(7,31))
                .then(Commands.argument("floors",IntegerArgumentType.integer(1,3)).executes(c->{
                    ServerPlayer p=c.getSource().getPlayerOrException();BlockPos pos=p.blockPosition();
                    int w=IntegerArgumentType.getInteger(c,"width"),l=IntegerArgumentType.getInteger(c,"length"),f=IntegerArgumentType.getInteger(c,"floors");
                    var d=KingdomDesignEngine.medievalHouse("medieval_house_"+w+"x"+l+"_"+f,w,l,f);
                    var claims=KingdomTerritories.get(p.getServer());
                    boolean allowed=KingdomDesignEngine.footprintAllowed(d,pos.getX(),pos.getZ(),(x,z)->claims.canUse(p.serverLevel(),new BlockPos(x,pos.getY(),z),p.getUUID()));
                    if(!allowed){say(c.getSource(),"Отказ: весь участок должен принадлежать тебе. Используй /knpc claim для каждого чанка.");return 0;}
                    var project=KingdomConstructionData.get(p.getServer()).add(p.getUUID(),p.level().dimension().location().toString(),d.name(),pos.getX(),pos.getY(),pos.getZ());
                    say(c.getSource(),"Архитектор разместил ПРОЕКТ "+project.id()+". Строительство пока не запущено; ресурсы: "+d.bill());return 1;
                }))))));
        root.then(Commands.literal("borders").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();
            boolean enabled=KingdomBorderOverlay.toggle(p);
            say(c.getSource(),enabled?"Границы участков включены (зелёный — город игрока, красный — чужой).":"Границы участков выключены.");
            return 1;
        }));
        root.then(Commands.literal("projects").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();int count=0;
            for(var project:KingdomConstructionData.get(p.getServer()).all())if(project.owner().equals(p.getUUID())){
                say(c.getSource(),project.design()+" @ "+project.x()+" "+project.y()+" "+project.z()+" ["+project.status()+"]");count++;
            }say(c.getSource(),"Проектов: "+count);return 1;
        }));
        root.then(Commands.literal("army")
            .then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.word()).executes(c->{
                ServerPlayer p=c.getSource().getPlayerOrException();String name=StringArgumentType.getString(c,"name");
                var unit=KingdomArmyData.get(p.getServer()).create(p.getUUID(),name);
                say(c.getSource(),"Создан реестр отряда "+name+" ID="+unit.id()+" (без спавна солдат)");return 1;
            })))
            .then(Commands.literal("list").executes(c->{
                ServerPlayer p=c.getSource().getPlayerOrException();int count=0;
                for(var u:KingdomArmyData.get(p.getServer()).all())if(u.owner().equals(p.getUUID())){say(c.getSource(),u.name()+" "+u.id()+" "+u.order());count++;}
                say(c.getSource(),"Отрядов: "+count);return 1;
            }))
            .then(Commands.literal("order").then(Commands.argument("unit_uuid",StringArgumentType.word())
                .then(Commands.argument("order",StringArgumentType.word()).suggests((c,b)->{
                    for(var o:KingdomArmyData.Order.values())b.suggest(o.name().toLowerCase(Locale.ROOT));return b.buildFuture();
                }).executes(c->{
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    try {
                        UUID id=UUID.fromString(StringArgumentType.getString(c,"unit_uuid"));
                        var order=KingdomArmyData.Order.valueOf(StringArgumentType.getString(c,"order").toUpperCase(Locale.ROOT));
                        boolean ok=KingdomArmyData.get(p.getServer()).order(id,p.getUUID(),order);
                        say(c.getSource(),ok?"Приказ записан: "+order:"Нет прав или отряд не найден");return ok?1:0;
                    }catch(IllegalArgumentException ex){say(c.getSource(),"Неверный UUID или приказ");return 0;}
                })))));
        e.getDispatcher().register(root);
    }
}
