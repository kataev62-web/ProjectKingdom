package ru.projectkingdom.administration;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import java.util.*;

public class KingdomBankVaultRooms {
    public enum Result { CREATED, EXPANDED, FIRST_CORNER, NO_SESSION, NO_RIGHTS, WRONG_DIMENSION, TOO_FAR, BANK_DESK_INSIDE, TOO_LARGE, EXISTING_ROOM_NOT_INCLUDED }
    
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    
    public record Session(UUID town, String dimension, BlockPos bank, BlockPos first) {}
    
    public static KingdomBankVaultRooms get(MinecraftServer s) { return new KingdomBankVaultRooms(); }
    public Object at(ServerLevel level, BlockPos pos) { return null; }
    public void begin(ServerPlayer player) {
        // Cancel warehouse session like enterprise/structure copy
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
    }
    public boolean hasSession(UUID playerId) { return SESSIONS.containsKey(playerId); }
    public static void cancel(UUID playerId) { SESSIONS.remove(playerId); }
    public Result select(ServerPlayer player, BlockPos pos) { return Result.CREATED; }
    public static String message(Result r) { return r.name(); }
}