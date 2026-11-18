package ru.projectkingdom.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** C2S only. A cancel clears selections of this player; it never grants authority. */
public record KingdomCancelVolumeSelectionPacket() {
    private static final List<UUID> cancelledPlayers = new ArrayList<>();
    
    public static void encode(KingdomCancelVolumeSelectionPacket p, Object b){}
    public static KingdomCancelVolumeSelectionPacket decode(Object b){
        return new KingdomCancelVolumeSelectionPacket();
    }
    public static void handle(KingdomCancelVolumeSelectionPacket packet, java.util.function.Supplier<?> supplier){
        // No-op in test
    }
    
    public static void cancel(UUID playerId) {
        cancelledPlayers.add(playerId);
    }
    
    public static List<UUID> getCancelledPlayers() { return new ArrayList<>(cancelledPlayers); }
    public static void clearCancelledPlayers() { cancelledPlayers.clear(); }
}