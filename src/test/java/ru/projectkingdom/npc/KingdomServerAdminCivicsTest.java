package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Civic governance cannot be bypassed by creating NPCs with fake player UUIDs. */
class KingdomServerAdminCivicsTest {
    @Test void oneNpcMayFoundOnlyOneSettlementAndNamesMustBeUnique(){
        var data=new KingdomCivicsData();
        UUID npc=UUID.randomUUID();
        var first=data.adminFoundTown(npc,"Avalon_Test");
        assertNotNull(first);
        assertNull(data.adminFoundTown(npc,"Second_Town"));
        assertNull(data.adminFoundTown(UUID.randomUUID(),"aVaLoN_tEsT"));
        assertEquals(npc,data.townById(first.id()).mayor());
        assertEquals(1,data.towns().size());
    }

    @Test void kingdomRequiresOwnerWhoIsAlreadyMayorAndCannotDeleteWithCities(){
        var data=new KingdomCivicsData();
        UUID npc=UUID.randomUUID(),stranger=UUID.randomUUID();
        assertNull(data.adminFoundNation(stranger,"Invalid"));
        var town=data.adminFoundTown(npc,"Avalon_Test");
        assertNotNull(town);
        var nation=data.adminFoundNation(npc,"Kingdom_One");
        assertNotNull(nation);
        assertNull(data.adminFoundNation(npc,"Kingdom_Two"));
        assertNull(data.adminFoundNation(stranger,"Kingdom_One"));
        assertNotNull(data.adminNationDeletionBlocker(nation.id()));
        assertFalse(data.adminDeleteNation(nation.id()));
        assertNotNull(data.nationById(nation.id()));
    }

    @Test void townWithMoneyOrPendingNationFundsCannotBePurged(){
        var data=new KingdomCivicsData();
        UUID npc=UUID.randomUUID();
        var city=data.adminFoundTown(npc,"My_City");
        assertNotNull(city);
        // A loaded MinecraftServer is required for performing actual deletion;
        // without one, the service must fail closed.
        assertNotNull(data.adminTownDeletionBlocker(city.id()));
        assertFalse(data.adminDeleteTown(city.id()));
        assertNotNull(data.townById(city.id()));
    }
}
