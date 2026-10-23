package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomEnterpriseData;

final class BankLegacyArchiveAndClosureTest {
    @Test void archivesMunicipalAccountingWithoutIssuingNewCoins() throws Exception {
        var town=UUID.randomUUID();
        var data=new KingdomAdministrationData();
        assertTrue(data.depositBankReserve(town,250));
        assertTrue(data.depositTreasuryReserve(town,140));
        data.archiveUnbackedReserves(town);
        assertEquals(0,data.bankReserve(town));
        assertEquals(0,data.treasuryReserve(town));
        assertEquals(250,data.legacyBankLiability(town));
        assertEquals(140,data.legacyTreasuryLiability(town));
        CompoundTag written=data.save(new CompoundTag());
        Method read=KingdomAdministrationData.class.getDeclaredMethod("fromTag",CompoundTag.class);
        read.setAccessible(true);
        var reloaded=(KingdomAdministrationData)read.invoke(null,written);
        assertEquals(250,reloaded.legacyBankLiability(town));
        assertEquals(140,reloaded.legacyTreasuryLiability(town));
        assertEquals(0,reloaded.bankReserve(town));
        assertEquals(0,reloaded.treasuryReserve(town));
        reloaded.archiveUnbackedReserves(town);
        assertEquals(250,reloaded.legacyBankLiability(town));
    }
    @Test void closedBusinessesSurviveWorldSaveAndCannotBeReactivated() throws Exception {
        var id=UUID.randomUUID();
        var owner=UUID.randomUUID();
        var root=new CompoundTag();
        var list=new net.minecraft.nbt.ListTag();
        var row=new CompoundTag();
        row.putUUID("Id",id);row.putUUID("Owner",owner);
        row.putString("Name","Старая кузница");row.putLong("Day",12);
        list.add(row);root.put("ClosedBusinesses",list);
        Method read=KingdomEnterpriseData.class.getDeclaredMethod("load",CompoundTag.class);
        read.setAccessible(true);
        var book=(KingdomEnterpriseData)read.invoke(null,root);
        assertTrue(book.wasClosed(id));
        assertNull(book.find(id));
        var saved=book.save(new CompoundTag());
        var again=(KingdomEnterpriseData)read.invoke(null,saved);
        assertTrue(again.wasClosed(id));
        assertNull(again.find(id));
    }
    @Test void enterpriseClosesOnlyWhenNoCashDebtsActiveBuildingOrStock() throws Exception {
        String business=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomEnterpriseData.java"));
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreMenu.java"));
        assertTrue(business.contains("firm.cash()!=0"));
        assertTrue(business.contains("unresolved(business)"));
        assertTrue(business.contains("updated.owed()!=0"));
        assertTrue(business.contains("container.isEmpty()"));
        assertTrue(business.contains("KindIsConstructionAndHasSite"));
        assertTrue(business.contains("closedBusinesses.put("));
        assertTrue(menu.contains("PENDING_CLOSURES"));
        assertTrue(menu.contains("CLOSE_BUSINESS"));
        assertTrue(menu.contains("data.mayManage(server.getUUID(),enterpriseId)"));
    }
}
