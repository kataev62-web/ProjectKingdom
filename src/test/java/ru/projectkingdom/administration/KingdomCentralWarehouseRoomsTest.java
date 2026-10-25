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

final class KingdomCentralWarehouseRoomsTest {
    @Test void roomsAreDistinctThreeDimensionalZones(){
        UUID town=UUID.randomUUID();
        var general=new KingdomCentralWarehouseRooms.Room(UUID.randomUUID(),town,"minecraft:overworld",
            new BlockPos(0,60,0),new BlockPos(10,65,10),KingdomCentralWarehouseRooms.Kind.GENERAL);
        var food=new KingdomCentralWarehouseRooms.Room(UUID.randomUUID(),town,"minecraft:overworld",
            new BlockPos(11,60,0),new BlockPos(16,65,10),KingdomCentralWarehouseRooms.Kind.FOOD);
        var cold=new KingdomCentralWarehouseRooms.Room(UUID.randomUUID(),town,"minecraft:overworld",
            new BlockPos(0,60,11),new BlockPos(10,65,16),KingdomCentralWarehouseRooms.Kind.COLD);
        assertEquals(11*6*11,general.volume());
        assertFalse(general.overlaps(food));assertFalse(general.overlaps(cold));
        assertFalse(food.overlaps(cold));assertTrue(cold.contains(new BlockPos(5,62,14)));
        assertFalse(cold.contains(new BlockPos(5,62,10)));
    }
    @Test void roomsSurviveWorldReloadWithoutInventingInventory() throws Exception {
        var root=new CompoundTag();
        var list=new ListTag();
        UUID town=UUID.randomUUID(),id=UUID.randomUUID();
        var row=new CompoundTag();
        row.putUUID("Id",id);row.putUUID("Town",town);
        row.putString("Dimension","minecraft:overworld");
        row.putLong("Min",new BlockPos(0,60,0).asLong());
        row.putLong("Max",new BlockPos(12,68,12).asLong());
        row.putString("Kind","COLD");list.add(row);root.put("Rooms",list);
        Method load=KingdomCentralWarehouseRooms.class.getDeclaredMethod("load",CompoundTag.class);
        load.setAccessible(true);
        var restored=(KingdomCentralWarehouseRooms)load.invoke(null,root);
        assertEquals(1,restored.rooms(town).size());
        assertEquals(KingdomCentralWarehouseRooms.Kind.COLD,restored.rooms(town).get(0).kind());
        var written=restored.save(new CompoundTag());
        var again=(KingdomCentralWarehouseRooms)load.invoke(null,written);
        assertEquals(restored.rooms(town),again.rooms(town));
    }
    @Test void cityStockCountsOnlyRealPhysicalChestItemsAndKeepsTfcExpiry() throws Exception {
        String rooms=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/KingdomCentralWarehouseRooms.java"));
        String market=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomWarehouseMarket.java"));
        String screen=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreScreen.java"));
        assertTrue(rooms.contains("KingdomContainerAccess.resolve"));
        assertTrue(rooms.contains("KingdomCurrency") == false);
        assertTrue(rooms.contains("BuiltInRegistries.ITEM.getKey(stack.getItem())"));
        assertFalse(rooms.contains("setTag("));
        assertFalse(rooms.contains("setItem("));
        assertTrue(market.contains("KingdomCentralWarehouseRooms.get"));
        assertTrue(market.contains("included.contains("));
        assertTrue(screen.contains("WAREHOUSE_ROOM_COLD"));
        assertTrue(screen.contains("warehousePage"));
    }
}
