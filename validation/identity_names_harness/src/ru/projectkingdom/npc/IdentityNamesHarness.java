package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class IdentityNamesHarness {
    private static void equal(String want,String actual){
        if(!want.equals(actual))throw new AssertionError("Expected «"+want+"», got «"+actual+"»");
    }
    public static void main(String[] args){
        var server=new MinecraftServer();
        UUID npc=UUID.randomUUID(),online=UUID.randomUUID(),offline=UUID.randomUUID(),unknown=UUID.randomUUID();
        KingdomNpcData.get(server).workers.add(new KingdomNpcData.Worker(npc,"Анна"));
        server.players.entries.put(npc,new MinecraftServer.Online(new MinecraftServer.Profile("WrongPlayer")));
        server.players.entries.put(online,new MinecraftServer.Online(new MinecraftServer.Profile("CurrentName")));
        server.cache.entries.put(online,new MinecraftServer.Profile("OldName"));
        server.cache.entries.put(offline,new MinecraftServer.Profile("OfflineMayor"));
        equal("Анна",KingdomIdentityNames.name(server,npc));
        equal("CurrentName",KingdomIdentityNames.name(server,online));
        equal("OfflineMayor",KingdomIdentityNames.name(server,offline));
        String fallback=KingdomIdentityNames.name(server,unknown);
        if(fallback.isBlank()||fallback.contains(unknown.toString())||fallback.contains(unknown.toString().substring(0,8)))
            throw new AssertionError("Unknown person must have a readable label without UUID: "+fallback);
        equal("Не назначен",KingdomIdentityNames.name(server,null));
        UUID blank=UUID.randomUUID(),longName=UUID.randomUUID(),malformed=UUID.randomUUID();
        KingdomNpcData.get(server).workers.add(new KingdomNpcData.Worker(blank," \t\n "));
        equal("Безымянный NPC",KingdomIdentityNames.name(server,blank));
        KingdomNpcData.get(server).workers.add(new KingdomNpcData.Worker(longName,"Я".repeat(63)+"😀"));
        equal("Я".repeat(63),KingdomIdentityNames.name(server,longName));
        KingdomNpcData.get(server).workers.add(new KingdomNpcData.Worker(malformed,"  Ирина\n\tПетрова§c  "));
        equal("Ирина Петрова",KingdomIdentityNames.name(server,malformed));
        server.cache=null;
        equal(fallback,KingdomIdentityNames.name(server,unknown));
        equal(fallback,KingdomIdentityNames.name(null,unknown));
        System.out.println("PASS identity display names: NPC/online/offline, safe labels, local-only lookup");
    }
}
