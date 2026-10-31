package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

public final class KingdomNpcData {
    private static final Map<MinecraftServer,KingdomNpcData> worlds=new WeakHashMap<>();
    public static KingdomNpcData get(MinecraftServer server){return worlds.computeIfAbsent(server,s->new KingdomNpcData());}
    public final Collection<Worker> workers=new ArrayList<>();
    public Collection<Worker> all(){return workers;}
    public static final class Worker {
        public final UUID entityId;
        public final String name;
        public Worker(UUID id,String name){entityId=id;this.name=name;}
    }
}
