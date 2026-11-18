package ru.projectkingdom.administration;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;

public class KingdomBankVaultRooms {
    public static KingdomBankVaultRooms get(MinecraftServer s) { return new KingdomBankVaultRooms(); }
    public Object at(ServerLevel level, BlockPos pos) { return null; }
    public void begin(ServerPlayer player) {
        // Cancel warehouse session like enterprise/structure copy
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
    }
}