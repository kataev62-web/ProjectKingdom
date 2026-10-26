package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.network.KingdomAdminActionPacket;
import ru.projectkingdom.network.KingdomAdminDashboardPacket;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Server-side Project Kingdom administration UI.
 * The client may request operations but cannot bypass OP4, active admin mode,
 * the server-owned session, civic access checks or destructive confirmation.
 */
public final class KingdomAdminDashboardService {
    private KingdomAdminDashboardService() {}
    public static final int PAGE_SIZE=16;
    private static final int MAX_PAGE=100_000;
    private static final UUID EMPTY=new UUID(0,0);
    private static final long SESSION_MS=15*60_000L;
    private static final long DELETE_CONFIRM_MS=120_000L;

    private static final class Session {
        final UUID token=UUID.randomUUID();
        long expires=System.currentTimeMillis()+SESSION_MS;
        int cityPage,nationPage,npcPage;
        UUID pending;
        boolean pendingNation;
        long pendingExpires;
    }
    private static final Map<UUID,Session> sessions=new HashMap<>();

    private static boolean allowed(ServerPlayer p){
        return p!=null && p.hasPermissions(4);
    }
    private static boolean active(ServerPlayer p){
        return allowed(p)&&KingdomServerAdminData.get(p.getServer()).enabled(p.getUUID());
    }
    public static void close(UUID player){
        sessions.remove(player);
    }
    public static void open(ServerPlayer p){
        if(!allowed(p))return;
        Session session=new Session();
        sessions.put(p.getUUID(),session);
        send(p,session,"Управление городами, королевствами и NPC");
    }
    private static String clean(String text,int max){
        if(text==null)return "";
        String value=text.replace('\n',' ').replace('\r',' ').replace('\t',' ');
        return value.length()>max?value.substring(0,max):value;
    }
    private static String npcName(UUID id,Map<UUID,String> names) {
        return id==null?"Не назначен":names.getOrDefault(id,
            "Игрок/UUID "+id.toString().substring(0,8));
    }
    private static <T> List<T> page(List<T> values,int page){
        int from=(int)Math.min(values.size(),(long)page*PAGE_SIZE);
        return List.copyOf(values.subList(from,Math.min(values.size(),from+PAGE_SIZE)));
    }
    private static void send(ServerPlayer p,Session s,String message){
        if(!allowed(p)){close(p.getUUID());return;}
        boolean enabled=active(p);
        List<KingdomAdminDashboardPacket.City> cities=new ArrayList<>();
        List<KingdomAdminDashboardPacket.Nation> nations=new ArrayList<>();
        List<KingdomAdminDashboardPacket.Npc> npcs=new ArrayList<>();
        if(enabled){
            var civics=KingdomCivicsData.get(p.getServer());
            var admin=KingdomAdministrationData.get(p.getServer());
            var npcDb=KingdomNpcData.get(p.getServer());
            Map<UUID,String> npcNames=new HashMap<>();
            for(var npc:npcDb.all()){
                npcNames.put(npc.entityId,clean("NPC #"+npc.id+" "+npc.name,70));
                npcs.add(new KingdomAdminDashboardPacket.Npc(npc.id,clean(npc.name,70),
                    npc.entityId,npc.ageYears));
            }
            npcs.sort(Comparator.comparingInt(KingdomAdminDashboardPacket.Npc::id));
            for(var town:civics.towns())
                cities.add(new KingdomAdminDashboardPacket.City(town.id(),town.name(),
                    npcName(town.mayor(),npcNames),npcName(admin.founder(town.id()),npcNames),
                    civics.townPlots(town.id(),0,32).size()));
            cities.sort(Comparator.comparing(KingdomAdminDashboardPacket.City::name,
                String.CASE_INSENSITIVE_ORDER).thenComparing(KingdomAdminDashboardPacket.City::id));
            for(var nation:civics.nations()){
                int townCount=0;
                for(var town:civics.towns())if(nation.id().equals(town.nation()))townCount++;
                nations.add(new KingdomAdminDashboardPacket.Nation(nation.id(),nation.name(),
                    npcName(nation.ruler(),npcNames),townCount));
            }
            nations.sort(Comparator.comparing(KingdomAdminDashboardPacket.Nation::name,
                String.CASE_INSENSITIVE_ORDER).thenComparing(KingdomAdminDashboardPacket.Nation::id));
        }
        s.cityPage=normalize(s.cityPage,cities.size());
        s.nationPage=normalize(s.nationPage,nations.size());
        s.npcPage=normalize(s.npcPage,npcs.size());
        boolean confirming=s.pending!=null && System.currentTimeMillis()<s.pendingExpires && enabled;
        if(!confirming)s.pending=null;
        KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),
            new KingdomAdminDashboardPacket(s.token,enabled,
                s.cityPage,cities.size(),page(cities,s.cityPage),
                s.nationPage,nations.size(),page(nations,s.nationPage),
                s.npcPage,npcs.size(),page(npcs,s.npcPage),
                message.length()>240?message.substring(0,240):message,
                confirming,confirming?s.pending:EMPTY,s.pendingNation));
    }
    private static int normalize(int page,int size){
        return Math.max(0,Math.min(page,Math.min(MAX_PAGE,
            Math.max(0,(size-1)/PAGE_SIZE))));
    }
    private static KingdomNpcData.Worker worker(ServerPlayer p,int id){
        var npc=KingdomNpcData.get(p.getServer()).workers.get(id);
        return npc!=null && npc.ageYears>=18 ? npc : null;
    }
    private static void audit(ServerPlayer p,String action){
        KingdomServerAdminData.get(p.getServer()).record(p.getUUID(),action);
    }
    private static String title(String name){
        if(name==null||name.isBlank()||name.length()>48||
                !name.matches("[\\p{L}\\p{N}_-]{1,48}"))
            return null;
        return name;
    }

    public static void act(ServerPlayer p,KingdomAdminActionPacket msg){
        if(!allowed(p))return;
        Session s=sessions.get(p.getUUID());
        if(s==null||!s.token.equals(msg.session())
                ||s.expires<System.currentTimeMillis()){
            close(p.getUUID());return;
        }
        s.expires=System.currentTimeMillis()+SESSION_MS;
        int action=msg.action();
        if(action<0||action>KingdomAdminActionPacket.NPC_PAGE)return;
        if(action==KingdomAdminActionPacket.TOGGLE){
            var data=KingdomServerAdminData.get(p.getServer());
            boolean next=!data.enabled(p.getUUID());
            data.set(p.getUUID(),next);
            audit(p,"ADMIN_MODE "+(next?"ON":"OFF")+" via GUI");
            s.pending=null;
            send(p,s,next?"Режим администратора включён.":"Режим администратора выключен.");
            return;
        }
        if(!active(p)){
            send(p,s,"Для управления включи роль администратора.");
            return;
        }
        var cities=KingdomCivicsData.get(p.getServer());
        String feedback="";
        var id=msg.target();
        KingdomNpcData.Worker npc=worker(p,msg.npcId());
        switch(action){
            case KingdomAdminActionPacket.REFRESH -> feedback="Данные обновлены.";
            case KingdomAdminActionPacket.CITY_PAGE -> {
                s.cityPage=Math.max(0,Math.min(MAX_PAGE,msg.page()));
                feedback="Страница городов.";
            }
            case KingdomAdminActionPacket.NATION_PAGE -> {
                s.nationPage=Math.max(0,Math.min(MAX_PAGE,msg.page()));
                feedback="Страница королевств.";
            }
            case KingdomAdminActionPacket.NPC_PAGE -> {
                s.npcPage=Math.max(0,Math.min(MAX_PAGE,msg.page()));
                feedback="Страница жителей.";
            }
            case KingdomAdminActionPacket.CREATE_CITY -> {
                String name=title(msg.name());
                if(npc==null || name==null)feedback="Выбери взрослого NPC и название до 48 букв.";
                else {
                    var result=cities.adminFoundTown(npc.entityId,name);
                    if(result==null)feedback="Не удалось создать город: имя занято или NPC уже основатель.";
                    else {
                        npc.settlement=result.name();
                        KingdomNpcData.get(p.getServer()).setDirty();
                        audit(p,"CREATE_CITY "+result.id()+" NPC #"+npc.id);
                        feedback="Создан город «"+result.name()+"». NPC #"+npc.id+" — основатель и мэр.";
                    }
                }
            }
            case KingdomAdminActionPacket.CREATE_NATION -> {
                String name=title(msg.name());
                if(npc==null||name==null)feedback="Выбери взрослого NPC-мэра и название.";
                else {
                    var result=cities.adminFoundNation(npc.entityId,name);
                    if(result==null)feedback="Для нового королевства NPC должен быть мэром независимого города.";
                    else {
                        audit(p,"CREATE_NATION "+result.id()+" NPC #"+npc.id);
                        feedback="Создано королевство «"+result.name()+"».";
                    }
                }
            }
            case KingdomAdminActionPacket.APPOINT_MAYOR,
                 KingdomAdminActionPacket.APPOINT_FOUNDER,
                 KingdomAdminActionPacket.APPOINT_RULER -> {
                if(npc==null){feedback="Выбери взрослого NPC справа.";}
                else {
                    boolean changed=switch(action) {
                        case KingdomAdminActionPacket.APPOINT_MAYOR -> cities.adminSetMayor(id,npc.entityId);
                        case KingdomAdminActionPacket.APPOINT_FOUNDER -> cities.adminSetFounder(id,npc.entityId);
                        default -> cities.adminSetRuler(id,npc.entityId);
                    };
                    if(!changed)feedback="Отказ: конфликт прав или NPC принадлежит другому поселению.";
                    else {
                        if(action!=KingdomAdminActionPacket.APPOINT_RULER){
                            var town=cities.townById(id);
                            if(town!=null){npc.settlement=town.name();KingdomNpcData.get(p.getServer()).setDirty();}
                        }
                        String role=action==KingdomAdminActionPacket.APPOINT_MAYOR?"мэром":
                            action==KingdomAdminActionPacket.APPOINT_FOUNDER?"основателем":"правителем";
                        audit(p,"APPOINT "+role+" "+id+" NPC #"+npc.id);
                        feedback="NPC #"+npc.id+" назначен "+role+".";
                    }
                }
            }
            case KingdomAdminActionPacket.CLAIM_CURRENT_CHUNK -> {
                if(p.level().dimension()==null){feedback="Измерение недоступно.";}
                else {
                    int cx=p.blockPosition().getX()>>4,cz=p.blockPosition().getZ()>>4;
                    boolean ok=cities.adminClaimTown(id,p.level().dimension().location().toString(),cx,cz);
                    if(ok){
                        audit(p,"CLAIM "+id+" "+cx+","+cz);
                        feedback="Чанк "+cx+","+cz+" закреплён за городом.";
                    }else feedback="Нельзя закрепить: чужой участок или город не существует.";
                }
            }
            case KingdomAdminActionPacket.PREPARE_CITY_DELETE,
                 KingdomAdminActionPacket.PREPARE_NATION_DELETE -> {
                boolean nation=action==KingdomAdminActionPacket.PREPARE_NATION_DELETE;
                String reason=nation?cities.adminNationDeletionBlocker(id):
                    cities.adminTownDeletionBlocker(id);
                if(reason!=null){
                    s.pending=null;feedback="Удаление запрещено: "+reason;
                }else{
                    s.pending=id;s.pendingNation=nation;
                    s.pendingExpires=System.currentTimeMillis()+DELETE_CONFIRM_MS;
                    feedback="Подтверди удаление: "+(nation?"королевство":"город")
                        +" будет удалено из реестра. Блоки мира сохранятся.";
                }
            }
            case KingdomAdminActionPacket.CONFIRM_DELETE -> {
                if(s.pending==null || !s.pending.equals(id)
                        ||s.pendingExpires<System.currentTimeMillis()){
                    feedback="Подтверждение истекло, повтори проверку.";
                }else{
                    boolean nation=s.pendingNation;
                    String reason=nation?cities.adminNationDeletionBlocker(id):
                        cities.adminTownDeletionBlocker(id);
                    if(reason!=null)feedback="Удаление отменено: "+reason;
                    else {
                        boolean removed=nation?cities.adminDeleteNation(id):cities.adminDeleteTown(id);
                        if(removed){
                            audit(p,"DELETE_"+(nation?"NATION ":"CITY ")+id+" via GUI");
                            feedback="Удалено из реестра. Блоки в мире не тронуты.";
                        }else feedback="Удаление не выполнено: есть зависимые данные.";
                    }
                }
                s.pending=null;
            }
            case KingdomAdminActionPacket.CANCEL_DELETE -> {
                s.pending=null;feedback="Удаление отменено.";
            }
            default -> {return;}
        }
        send(p,s,feedback);
    }
}
