package ru.projectkingdom.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /kingdom admin: server-authoritative world administration, never a player rank hack. */
public final class KingdomServerAdminCommands {
    private KingdomServerAdminCommands() {}
    private record Pending(UUID target,String type,String token,long expires){}
    private static final Map<UUID,Pending> PENDING=new HashMap<>();

    private static void reply(CommandSourceStack source,String message){
        source.sendSuccess(()->Component.literal("[Администратор] "+message),false);
    }
    private static boolean enabled(CommandSourceStack source){
        return source.hasPermission(4) && (
            source.getEntity()==null ||
            source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p &&
            KingdomServerAdminData.get(source.getServer()).enabled(p.getUUID()));
    }
    private static UUID actor(CommandSourceStack src){
        return src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p
            ?p.getUUID():new UUID(0L,0L);
    }
    private static KingdomNpcData.Worker worker(CommandSourceStack src,int id){
        var npc=KingdomNpcData.get(src.getServer()).workers.get(id);
        if(npc==null||npc.ageYears<18){
            reply(src,"NPC #"+id+" не существует или несовершеннолетний.");
            return null;
        }
        return npc;
    }
    private static UUID parse(CommandSourceStack src,String raw){
        try{return UUID.fromString(raw);}
        catch(IllegalArgumentException e){reply(src,"Используй полный UUID из команды list.");return null;}
    }
    private static void log(CommandSourceStack src,String action){
        KingdomServerAdminData.get(src.getServer()).record(actor(src),action);
    }
    private static int require(CommandSourceStack src){
        if(!enabled(src)){reply(src,"Сначала включи роль командой /kingdom admin on (нужны OP-права уровня 4).");return 0;}
        return 1;
    }
    private static int setMode(CommandSourceStack src,boolean mode){
        if(!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)){
            reply(src,"Консоль имеет полномочия по умолчанию.");return 1;
        }
        KingdomServerAdminData data=KingdomServerAdminData.get(src.getServer());
        data.set(player.getUUID(),mode);
        data.record(player.getUUID(),"ADMIN_MODE "+(mode?"ON":"OFF"));
        reply(src,mode?"Роль АДМИНИСТРАТОР включена.":"Роль АДМИНИСТРАТОР выключена.");
        return 1;
    }
    private static int toggle(CommandSourceStack src){
        if(!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p))
            return setMode(src,true);
        return setMode(src,!KingdomServerAdminData.get(src.getServer()).enabled(p.getUUID()));
    }

    private static int list(CommandSourceStack src,boolean nation){
        if(require(src)==0)return 0;
        var cities=KingdomCivicsData.get(src.getServer());
        if(nation)for(var n:cities.nations())
            reply(src,"Королевство "+n.name()+" UUID="+n.id()+" правитель="+n.ruler()+" казна="+n.treasury());
        else for(var town:cities.towns()){
            var a=ru.projectkingdom.administration.KingdomAdministrationData.get(src.getServer());
            reply(src,"Город "+town.name()+" UUID="+town.id()+" мэр="+town.mayor()
                +" основатель="+a.founder(town.id())+" королевство="+town.nation());
        }
        return 1;
    }
    private static int createCity(CommandSourceStack src,String name,int workerId){
        if(require(src)==0)return 0;
        var npc=worker(src,workerId);
        if(npc==null)return 0;
        var city=KingdomCivicsData.get(src.getServer()).adminFoundTown(npc.entityId,name);
        if(city==null){reply(src,"Не удалось создать город: такое имя занято или NPC уже основал поселение.");return 0;}
        npc.settlement=name;
        KingdomNpcData.get(src.getServer()).setDirty();
        log(src,"CREATE_CITY "+city.id()+" OWNER_NPC #"+workerId);
        reply(src,"Создан город «"+name+"», UUID="+city.id()+". NPC #"+workerId
            +" стал основателем и мэром. Используй city claim для назначения первого участка.");
        return 1;
    }
    private static int createNation(CommandSourceStack src,String name,int workerId){
        if(require(src)==0)return 0;
        var npc=worker(src,workerId);if(npc==null)return 0;
        var result=KingdomCivicsData.get(src.getServer()).adminFoundNation(npc.entityId,name);
        if(result==null){reply(src,"Для основания королевства NPC должен быть мэром собственного города, не входящего в другое королевство.");return 0;}
        log(src,"CREATE_NATION "+result.id()+" RULER_NPC #"+workerId);
        reply(src,"Создано королевство «"+result.name()+"», UUID="+result.id()+"; правитель NPC #"+workerId);
        return 1;
    }
    private static int appoint(CommandSourceStack src,String type,String id,int workerId){
        if(require(src)==0)return 0;
        UUID target=parse(src,id);if(target==null)return 0;
        var npc=worker(src,workerId);if(npc==null)return 0;
        var data=KingdomCivicsData.get(src.getServer());
        boolean changed=switch(type){
            case "mayor"->data.adminSetMayor(target,npc.entityId);
            case "founder"->data.adminSetFounder(target,npc.entityId);
            case "ruler"->data.adminSetRuler(target,npc.entityId);
            default->false;
        };
        if(!changed){reply(src,"Отказ: город/королевство не найдено, NPC состоит в другом городе или уже основал другое поселение.");return 0;}
        if(!type.equals("ruler")){
            var town=data.townById(target);
            if(town!=null){npc.settlement=town.name();KingdomNpcData.get(src.getServer()).setDirty();}
        }
        log(src,"APPOINT_"+type.toUpperCase(java.util.Locale.ROOT)+" "+target+" NPC #"+workerId);
        reply(src,"Назначено: "+type+" → NPC #"+workerId+". Сохранено в данных королевства.");
        return 1;
    }
    private static int claim(CommandSourceStack src,String raw){
        if(require(src)==0)return 0;
        UUID id=parse(src,raw);if(id==null)return 0;
        if(!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)){
            reply(src,"Для выбора участка нужно находиться в игре.");return 0;
        }
        boolean ok=KingdomCivicsData.get(src.getServer()).adminClaimTown(id,
            p.level().dimension().location().toString(),
            p.blockPosition().getX()>>4,p.blockPosition().getZ()>>4);
        if(!ok){reply(src,"Участок уже занят, город не найден или есть чужие права.");return 0;}
        log(src,"CITY_CLAIM "+id+" "+p.blockPosition().toShortString());
        reply(src,"Текущий чанк закреплён за городом. Блоки и деньги не менялись.");
        return 1;
    }
    private static String blocker(CommandSourceStack src,String type,UUID id){
        var d=KingdomCivicsData.get(src.getServer());
        return type.equals("city")?d.adminTownDeletionBlocker(id):d.adminNationDeletionBlocker(id);
    }
    private static int prepare(CommandSourceStack src,String type,String raw){
        if(require(src)==0)return 0;
        UUID id=parse(src,raw);if(id==null)return 0;
        String reason=blocker(src,type,id);
        if(reason!=null){reply(src,"Удаление заблокировано: "+reason);return 0;}
        UUID actor=actor(src);
        String code=UUID.randomUUID().toString().substring(0,8);
        PENDING.put(actor,new Pending(id,type,code,System.currentTimeMillis()+120000));
        reply(src,"ПОДТВЕРЖДЕНИЕ: удалить "+(type.equals("city")?"город":"королевство")+" "+id
            +"? Все данные города/его участки будут удалены из реестров, но постройки в мире останутся."
            +" Выполни /kingdom admin "+type+" delete "+id+" "+code+" за 2 минуты.");
        return 1;
    }
    private static int delete(CommandSourceStack src,String type,String raw,String code){
        if(require(src)==0)return 0;
        UUID id=parse(src,raw);if(id==null)return 0;
        Pending pending=PENDING.remove(actor(src));
        if(pending==null||!pending.target().equals(id)||!pending.type().equals(type)
                ||!pending.token().equals(code)||System.currentTimeMillis()>pending.expires()){
            reply(src,"Неверное подтверждение или срок истёк. Сначала команда preview.");return 0;
        }
        var d=KingdomCivicsData.get(src.getServer());
        String reason=blocker(src,type,id);
        if(reason!=null){reply(src,"Отказ: данные изменились после предпросмотра: "+reason);return 0;}
        boolean ok=type.equals("city")?d.adminDeleteTown(id):d.adminDeleteNation(id);
        if(!ok){reply(src,"Операция не выполнена. Ничего не изменено.");return 0;}
        log(src,"DELETE_"+type.toUpperCase(java.util.Locale.ROOT)+" "+id);
        reply(src,"Из реестра удалено: "+type+" "+id+". Блоки мира сохранены. Действие записано в журнал.");
        return 1;
    }
    public static LiteralArgumentBuilder<CommandSourceStack> root(){
        var root=Commands.literal("admin").requires(s->s.hasPermission(4))
            .executes(c->toggle(c.getSource()))
            .then(Commands.literal("on").executes(c->setMode(c.getSource(),true)))
            .then(Commands.literal("off").executes(c->setMode(c.getSource(),false)))
            .then(Commands.literal("status").executes(c->{
                reply(c.getSource(),enabled(c.getSource())?"Роль администратора активна.":"Роль администратора не активна.");return 1;}))
            .then(Commands.literal("audit").executes(c->{
                if(require(c.getSource())==0)return 0;
                KingdomServerAdminData.get(c.getSource().getServer()).audit(15)
                    .forEach(line->reply(c.getSource(),line));return 1;}));
        root.then(Commands.literal("city")
            .then(Commands.literal("list").executes(c->list(c.getSource(),false)))
            .then(Commands.literal("create")
                .then(Commands.argument("name",StringArgumentType.word())
                .then(Commands.argument("npc",IntegerArgumentType.integer(1))
                    .executes(c->createCity(c.getSource(),
                        StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"npc"))))))
            .then(Commands.literal("mayor")
                .then(Commands.argument("uuid",StringArgumentType.word())
                .then(Commands.argument("npc",IntegerArgumentType.integer(1)).executes(c->
                    appoint(c.getSource(),"mayor",StringArgumentType.getString(c,"uuid"),
                        IntegerArgumentType.getInteger(c,"npc"))))))
            .then(Commands.literal("founder")
                .then(Commands.argument("uuid",StringArgumentType.word())
                .then(Commands.argument("npc",IntegerArgumentType.integer(1)).executes(c->
                    appoint(c.getSource(),"founder",StringArgumentType.getString(c,"uuid"),
                        IntegerArgumentType.getInteger(c,"npc"))))))
            .then(Commands.literal("claim")
                .then(Commands.argument("uuid",StringArgumentType.word()).executes(c->
                    claim(c.getSource(),StringArgumentType.getString(c,"uuid")))))
            .then(Commands.literal("preview")
                .then(Commands.argument("uuid",StringArgumentType.word()).executes(c->
                    prepare(c.getSource(),"city",StringArgumentType.getString(c,"uuid")))))
            .then(Commands.literal("delete")
                .then(Commands.argument("uuid",StringArgumentType.word())
                .then(Commands.argument("code",StringArgumentType.word()).executes(c->
                    delete(c.getSource(),"city",StringArgumentType.getString(c,"uuid"),
                        StringArgumentType.getString(c,"code")))))));
        root.then(Commands.literal("kingdom")
            .then(Commands.literal("list").executes(c->list(c.getSource(),true)))
            .then(Commands.literal("create")
                .then(Commands.argument("name",StringArgumentType.word())
                .then(Commands.argument("npc",IntegerArgumentType.integer(1))
                    .executes(c->createNation(c.getSource(),
                        StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"npc"))))))
            .then(Commands.literal("ruler")
                .then(Commands.argument("uuid",StringArgumentType.word())
                .then(Commands.argument("npc",IntegerArgumentType.integer(1)).executes(c->
                    appoint(c.getSource(),"ruler",StringArgumentType.getString(c,"uuid"),
                        IntegerArgumentType.getInteger(c,"npc"))))))
            .then(Commands.literal("preview")
                .then(Commands.argument("uuid",StringArgumentType.word()).executes(c->
                    prepare(c.getSource(),"kingdom",StringArgumentType.getString(c,"uuid")))))
            .then(Commands.literal("delete")
                .then(Commands.argument("uuid",StringArgumentType.word())
                .then(Commands.argument("code",StringArgumentType.word()).executes(c->
                    delete(c.getSource(),"kingdom",StringArgumentType.getString(c,"uuid"),
                        StringArgumentType.getString(c,"code")))))));
        return root;
    }
}
