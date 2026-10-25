package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.property.KingdomRentalsData;
import ru.projectkingdom.property.KingdomRentalsData.Bounds;
import ru.projectkingdom.property.KingdomRentalsData.Purpose;
import ru.projectkingdom.property.KingdomRentalsData.Access;

class KingdomRentalsDataTest {
    private static final String DIM="minecraft:overworld";
    private final UUID owner=UUID.randomUUID();
    private final UUID tenant=UUID.randomUUID();
    private final UUID other=UUID.randomUUID();
    private final KingdomCivicsData land=new KingdomCivicsData();
    private final KingdomRentalsData rentals=new KingdomRentalsData();

    private KingdomRentalsData.Building home(){
        assertNotNull(land.foundTown(owner,"Авалон"));
        assertTrue(land.claim(owner,DIM,0,0));
        var b=rentals.registerBuilding(land,owner,DIM,"Доходный дом",
                new Bounds(1,63,1,13,76,13));
        assertNotNull(b);return b;
    }

    @Test void independentRoomsOnDifferentFloorsAndRights() {
        var building=home();
        var first=rentals.createRoom(land,owner,building.id(),"Комната 1",Purpose.HOME,
                new Bounds(2,64,2,6,67,6));
        var second=rentals.createRoom(land,owner,building.id(),"Комната 2",Purpose.SHOP,
                new Bounds(2,69,2,6,72,6));
        assertNotNull(first);assertNotNull(second);
        assertEquals(2,rentals.rooms().size());
        assertEquals(first.id(),rentals.atRoom(DIM,new BlockPos(3,65,3)).id());
        assertEquals(second.id(),rentals.atRoom(DIM,new BlockPos(3,70,3)).id());
        assertNull(rentals.createRoom(land,owner,building.id(),"Пересечение",Purpose.HOME,
                new Bounds(3,65,3,7,70,7)));

        assertTrue(rentals.configure(land,owner,first.id(),50,7,true));
        long now=1_800_000_000_000L;
        assertTrue(rentals.canRent(land,tenant,first.id(),now));
        assertFalse(rentals.canRent(land,owner,first.id(),now));
        assertTrue(rentals.rentPaid(land,tenant,first.id(),now));
        assertFalse(rentals.rentPaid(land,other,first.id(),now));
        assertEquals(Access.ALLOWED,rentals.roomAccess(tenant,DIM,
                new BlockPos(4,65,4),KingdomCivicsData.Action.BUILD,now+1));
        assertEquals(Access.DENIED,rentals.roomAccess(owner,DIM,
                new BlockPos(4,65,4),KingdomCivicsData.Action.INTERACT,now+1));
        assertEquals(Access.DENIED,rentals.roomAccess(other,DIM,
                new BlockPos(4,65,4),KingdomCivicsData.Action.INTERACT,now+1));
        assertEquals(Access.DENIED,rentals.roomAccess(tenant,DIM,
                new BlockPos(2,65,4),KingdomCivicsData.Action.BREAK,now+1));
        assertEquals(Access.ALLOWED,rentals.roomAccess(tenant,DIM,
                new BlockPos(2,65,4),KingdomCivicsData.Action.INTERACT,now+1));
        assertEquals(Access.OUTSIDE,rentals.roomAccess(tenant,DIM,
                new BlockPos(10,70,10),KingdomCivicsData.Action.BUILD,now+1));
        assertTrue(rentals.canRenew(land,tenant,first.id(),now+10));
        assertTrue(rentals.renewPaid(land,tenant,first.id(),now+10));
        assertEquals(now+14*KingdomRentalsData.DAY,rentals.room(first.id()).expiresAt());
        assertFalse(rentals.surrender(owner,first.id()));
        assertTrue(rentals.surrender(tenant,first.id()));
        assertEquals(Access.ALLOWED,rentals.roomAccess(owner,DIM,
                new BlockPos(4,65,4),KingdomCivicsData.Action.BUILD,now+1));
    }

    @Test void titleOfEveryTouchedChunkIsMandatory() {
        assertNotNull(land.foundTown(owner,"Авалон"));
        assertTrue(land.claim(owner,DIM,0,0));
        var box=new Bounds(13,64,2,19,72,9);
        assertNull(rentals.registerBuilding(land,owner,DIM,"Нельзя без земли",box));
        assertTrue(land.claim(owner,DIM,1,0));
        var building=rentals.registerBuilding(land,owner,DIM,"Второй участок",box);
        assertNotNull(building);
        assertNull(rentals.registerBuilding(land,owner,DIM,"Пересечение",box));
        assertNull(rentals.registerBuilding(land,other,DIM,"Чужой дом",
                new Bounds(2,64,2,5,69,5)));
        assertNull(rentals.registerBuilding(land,owner,"minecraft:the_nether","Не наша земля",box));
    }

    @Test void expirationCannotCreateCoinsOrForciblyRenewOccupancy() {
        var house=home();
        var room=rentals.createRoom(land,owner,house.id(),"Комната",Purpose.HOME,
                new Bounds(2,64,2,7,69,7));
        assertNotNull(room);
        long now=2_000_000_000_000L;
        assertTrue(rentals.configure(land,owner,room.id(),20,1,true));
        assertTrue(rentals.rentPaid(land,tenant,room.id(),now));
        assertEquals(Access.DENIED,rentals.roomAccess(tenant,DIM,
                new BlockPos(3,66,3),KingdomCivicsData.Action.INTERACT,now+KingdomRentalsData.DAY+1));
        assertFalse(rentals.canRenew(land,tenant,room.id(),now+KingdomRentalsData.DAY+1));
        assertFalse(rentals.configure(land,owner,room.id(),30,7,true));
        assertFalse(rentals.releaseExpired(land,other,room.id(),now+KingdomRentalsData.DAY+1));
        assertTrue(rentals.releaseExpired(land,owner,room.id(),now+KingdomRentalsData.DAY+1));
        assertNull(rentals.room(room.id()).tenant());
        assertFalse(rentals.room(room.id()).listed());
    }

    @Test void buildingRoomAndLeaseSurviveServerSaveLoad() {
        var house=home();
        var room=rentals.createRoom(land,owner,house.id(),"Пекарня",Purpose.SHOP,
                new Bounds(2,64,2,8,67,8));
        assertNotNull(room);
        assertTrue(rentals.configure(land,owner,room.id(),500,30,true));
        long now=1_900_000_000_000L;
        assertTrue(rentals.rentPaid(land,tenant,room.id(),now));
        KingdomRentalsData loaded=KingdomRentalsData.load(rentals.save(new CompoundTag()));
        assertEquals(house,loaded.building(house.id()));
        assertEquals(rentals.room(room.id()),loaded.room(room.id()));
        assertEquals(1,loaded.rooms().size());
    }

    @Test void boundsLimitAndStructuralSurfaces() {
        assertThrows(IllegalArgumentException.class,()->
                new Bounds(0,0,0,70,1,2));
        assertThrows(IllegalArgumentException.class,()->
                new Bounds(0,0,0,63,47,63));
        var b=Bounds.between(new BlockPos(5,65,4),new BlockPos(1,68,1));
        assertTrue(b.contains(new BlockPos(3,66,2)));
        assertTrue(b.structural(new BlockPos(1,66,2)));
        assertFalse(b.structural(new BlockPos(3,66,2)));
    }
}
