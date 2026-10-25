package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import ru.projectkingdom.network.ArchitectCatalogActionPacket;
import ru.projectkingdom.network.ArchitectCatalogPacket;
import ru.projectkingdom.network.ArchitectPreviewPacket;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Native Project Kingdom architect UI, not an inherited ChestMenu.
 * A server-issued token binds the selected plan and office. No arbitrary
 * source-file path, world coordinate or source block list arrives from a client.
 */
public final class KingdomArchitectCatalog {
    private KingdomArchitectCatalog() {}
    private record Selection(String key,String title,int width,int height,int length,int blocks){}
    private record Session(UUID token,BlockPos office,String dimension,long expiry,List<Selection> entries){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final long DURATION=20L*60L*10L;

    public static void close(UUID player){SESSIONS.remove(player);}

    public static void open(ServerPlayer player, BlockPos sourceOffice){
        BlockPos office=sourceOffice==null?KingdomSiteBuilder.findLectern(player):sourceOffice;
        if(office==null||!KingdomArchitectSiteAccess.mayUse(player,office)
                ||player.distanceToSqr(office.getX()+.5,office.getY()+.5,office.getZ()+.5)>144){
            tell(player,"Подойди к своей строительной конторе или чертёжному столу.");
            return;
        }
        var entries=new ArrayList<Selection>();
        for(var type:KingdomBuildingTypes.Type.values()){
            try {
                var design=KingdomArchitectDesignRotation.rotate(KingdomBuildingTypes.design(type),0);
                entries.add(new Selection("b:"+type.name(),type.title,design.width(),
                    design.height(),design.length(),design.blocks().size()));
            }catch(IllegalArgumentException ex){
                tell(player,"Пропущен встроенный чертёж "+type.title+": "+ex.getMessage());
            }
        }
        for(String name:KingdomSchematicLibrary.available(player.getServer())){
            try {
                var plan=KingdomSchematicLibrary.preview(player.getServer(),name);
                var design=KingdomArchitectDesignRotation.rotate(plan,0);
                entries.add(new Selection("s:"+name,KingdomSchematicLibrary.displayName(name),
                    design.width(),design.height(),design.length(),design.blocks().size()));
            }catch(IOException|IllegalArgumentException ex){
                tell(player,"Схема "+name+" не загружена: "+ex.getMessage());
            }
        }
        if(entries.isEmpty()){tell(player,"Нет доступных построек.");return;}
        Session session=new Session(UUID.randomUUID(),office.immutable(),
            player.level().dimension().location().toString(),
            player.serverLevel().getGameTime()+DURATION,List.copyOf(entries));
        SESSIONS.put(player.getUUID(),session);
        send(player,session,0);
    }

    public static void act(ServerPlayer player,ArchitectCatalogActionPacket packet){
        Session session=SESSIONS.get(player.getUUID());
        if(session==null || !session.token().equals(packet.token())
                ||session.expiry()<player.serverLevel().getGameTime()
                || !session.dimension().equals(player.level().dimension().location().toString())
                || !KingdomArchitectSiteAccess.mayUse(player,session.office())
                || player.distanceToSqr(session.office().getX()+.5,
                    session.office().getY()+.5,session.office().getZ()+.5)>144) {
            tell(player,"Каталог устарел или контора недоступна. Открой архитектора заново.");
            return;
        }
        switch(packet.action()){
            case ArchitectCatalogActionPacket.SELECT -> {
                if(packet.index()<0||packet.index()>=session.entries().size())return;
                send(player,session,packet.index());
            }
            case ArchitectCatalogActionPacket.PLACE -> {
                if(packet.index()<0||packet.index()>=session.entries().size())return;
                Selection choice=session.entries().get(packet.index());
                if(choice.key().startsWith("b:")){
                    try {
                        KingdomArchitectSessions.beginBuiltin(player,
                            KingdomBuildingTypes.Type.valueOf(choice.key().substring(2)),session.office());
                    }catch(IllegalArgumentException ignored) {
                        tell(player,"Проект больше недоступен.");
                    }
                } else {
                    KingdomArchitectSessions.beginSchematic(player,choice.key().substring(2),session.office());
                }
            }
            case ArchitectCatalogActionPacket.COPY -> {
                if(!KingdomSchematicLibrary.validName(packet.copyName())){
                    tell(player,"Имя чертежа: 1–48 символов, латинские a-z, цифры, _ или -.");
                    return;
                }
                KingdomStructureCopy.begin(player,session.office(),packet.copyName());
            }
            default -> { }
        }
    }

    private static void send(ServerPlayer player,Session session,int index){
        if(index<0||index>=session.entries().size())return;
        Selection selected=session.entries().get(index);
        try {
            KingdomDesignEngine.Design original=
                selected.key().startsWith("b:")
                ?KingdomBuildingTypes.design(KingdomBuildingTypes.Type.valueOf(selected.key().substring(2)))
                :KingdomSchematicLibrary.preview(player.getServer(),selected.key().substring(2));
            var design=KingdomArchitectDesignRotation.rotate(original,0);
            var miniature=ArchitectPreviewPacket.fromDesign(design,session.token(),
                session.office(),selected.title());
            if(miniature==null)throw new IOException("Слишком сложная модель для 3D-рисунка.");
            var entries=new ArrayList<ArchitectCatalogPacket.Entry>();
            for(var item:session.entries())entries.add(new ArchitectCatalogPacket.Entry(
                item.key(),item.title(),item.width(),item.height(),item.length(),item.blocks()));
            var materials=KingdomConstructionPlans.bill(design).entrySet().stream()
                .sorted(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue)
                    .reversed())
                .limit(12)
                .map(e->new ArchitectCatalogPacket.Material(e.getKey(),e.getValue()))
                .toList();
            KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),
                new ArchitectCatalogPacket(session.token(),List.copyOf(entries),index,miniature,materials));
        }catch(IOException|IllegalArgumentException ex){
            tell(player,"Не удалось показать проект: "+ex.getMessage());
        }
    }

    private static void tell(ServerPlayer player,String message){
        player.sendSystemMessage(Component.literal("[Архитектор] "+message));
    }
}
