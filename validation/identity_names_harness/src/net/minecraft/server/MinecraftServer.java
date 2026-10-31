package net.minecraft.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Only the local profile boundary; name/repository lookups deliberately fail. */
public final class MinecraftServer {
    public final Players players=new Players();
    public Profiles cache=new Profiles();
    public Players getPlayerList(){return players;}
    public Profiles getProfileCache(){return cache;}
    public record Profile(String name){public String getName(){return name;}}
    public record Online(Profile profile){public Profile getGameProfile(){return profile;}}
    public static final class Players {
        public final Map<UUID,Online> entries=new HashMap<>();
        public Online getPlayer(UUID id){return entries.get(id);}
    }
    public static final class Profiles {
        public final Map<UUID,Profile> entries=new HashMap<>();
        public Optional<Profile> get(UUID id){return Optional.ofNullable(entries.get(id));}
        public Optional<Profile> get(String name){throw new AssertionError("Blocking name lookup forbidden");}
    }
    public Object getProfileRepository(){throw new AssertionError("Network lookup forbidden");}
}
