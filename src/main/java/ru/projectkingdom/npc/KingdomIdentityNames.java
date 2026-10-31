package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Server-owned presentation only. Identity and permissions always remain UUID-based. */
public final class KingdomIdentityNames {
    private KingdomIdentityNames(){}

    public static String name(MinecraftServer server,UUID id){
        if(id==null)return "Не назначен";
        if(server==null)return "Неизвестный житель";
        for(var worker:KingdomNpcData.get(server).all())
            if(id.equals(worker.entityId))return npcName(worker.name);
        var online=server.getPlayerList().getPlayer(id);
        if(online!=null)return label(online.getGameProfile().getName(),"Неизвестный житель");
        // UserCache#get(UUID) reads the local cache only. Never use get(String),
        // profile repositories or remote lookups while opening a menu.
        var cache=server.getProfileCache();
        return label(cache==null?null:cache.get(id).map(profile->profile.getName()).orElse(null),
                "Неизвестный житель");
    }

    static String npcName(String value){return label(value,"Безымянный NPC");}

    private static String label(String value,String fallback){
        if(value==null)return fallback;
        String clean=value.replaceAll("§.","").replaceAll("[\\p{Cntrl}\\p{Z}\\s]+"," ").trim();
        if(clean.isBlank())return fallback;
        int end=Math.min(64,clean.length());
        if(end<clean.length()&&Character.isHighSurrogate(clean.charAt(end-1)))end--;
        return clean.substring(0,end);
    }
}
