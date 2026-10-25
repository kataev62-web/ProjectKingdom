package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class KingdomBankVaultRoomTest {
    @Test void roomHasThreeDimensionalBoundsAndNoCrossTownOverlap(){
        UUID town=UUID.randomUUID();
        var room=new KingdomBankVaultRooms.Room(town,"minecraft:overworld",
                new BlockPos(-2,60,3),new BlockPos(4,65,9));
        assertEquals(7*6*7,room.volume());
        assertTrue(room.contains(new BlockPos(0,63,5)));
        assertFalse(room.contains(new BlockPos(0,66,5)));
        assertFalse(room.contains(new BlockPos(7,63,5)));
        var expanded=new KingdomBankVaultRooms.Room(town,"minecraft:overworld",
                new BlockPos(-4,59,2),new BlockPos(5,67,10));
        assertTrue(expanded.contains(room));
        assertFalse(room.contains(expanded));
        assertTrue(room.overlaps(expanded));
        var separate=new KingdomBankVaultRooms.Room(UUID.randomUUID(),"minecraft:overworld",
                new BlockPos(6,60,3),new BlockPos(10,65,9));
        assertFalse(room.overlaps(separate));
        assertFalse(room.overlaps(new KingdomBankVaultRooms.Room(UUID.randomUUID(),
                "minecraft:the_nether",room.min(),room.max())));
    }
    @Test void roomPersistsExactlyAndCannotBeOversizedOnReload() throws Exception {
        UUID town=UUID.randomUUID();
        CompoundTag valid=new CompoundTag();
        valid.putUUID("Town",town);
        valid.putString("Dimension","minecraft:overworld");
        valid.putLong("Min",new BlockPos(1,60,1).asLong());
        valid.putLong("Max",new BlockPos(13,67,15).asLong());
        ListTag rooms=new ListTag();rooms.add(valid);
        CompoundTag root=new CompoundTag();root.put("Rooms",rooms);
        Method load=KingdomBankVaultRooms.class.getDeclaredMethod("load",CompoundTag.class);
        load.setAccessible(true);
        var state=(KingdomBankVaultRooms)load.invoke(null,root);
        assertEquals(13*8*15,state.room(town).volume());
        var copy=(KingdomBankVaultRooms)load.invoke(null,state.save(new CompoundTag()));
        assertEquals(state.room(town),copy.room(town));
        valid.putLong("Max",new BlockPos(250,67,15).asLong());
        var oversized=(KingdomBankVaultRooms)load.invoke(null,root);
        assertNull(oversized.room(town));
    }
    @Test void bankCashAndProtectionUseTheWholeLoadedRoomNotOneChest() throws Exception {
        String cash=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomPhysicalCash.java"));
        String rooms=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/KingdomBankVaultRooms.java"));
        String protection=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/MunicipalVaultProtection.java"));
        String packet=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java"));
        assertTrue(cash.contains("rooms.vault(actor.serverLevel(),town)"));
        assertTrue(rooms.contains("new VaultContainer(contents)"));
        assertTrue(rooms.contains("MAX_CONTAINERS=64"));
        assertTrue(rooms.contains("KingdomCurrency.value(stack)<=0"));
        assertTrue(rooms.contains("KingdomContainerAccess.samePhysicalStorage"));
        assertTrue(protection.contains("KingdomBankVaultRooms.get(level.getServer()).at(level"));
        assertTrue(protection.contains("BlockEvent.EntityPlaceEvent"));
        assertTrue(packet.contains("KingdomBankVaultRooms.get(sender.getServer()).begin(sender)"));
    }
}
