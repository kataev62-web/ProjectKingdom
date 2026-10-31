package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class KingdomExpansionTreasuryDebitTest {
    @Test void sameReceiptCannotChargeTwiceOrRefundTwice() {
        KingdomAdministrationData admin = new KingdomAdministrationData();
        UUID city = UUID.randomUUID(), tx = UUID.randomUUID();
        assertTrue(admin.depositTreasuryReserve(city, 500));
        assertTrue(admin.debitExpansion(city, tx, 200));
        assertTrue(admin.debitExpansion(city, tx, 200));
        assertEquals(300, admin.treasuryReserve(city));
        assertEquals(200,admin.heldExpansionProceeds(city));
        assertFalse(admin.debitExpansion(city, tx, 201));
        assertFalse(admin.debitExpansion(UUID.randomUUID(), tx, 200));
        assertTrue(admin.refundExpansion(tx));
        assertTrue(admin.refundExpansion(tx));
        assertEquals(500, admin.treasuryReserve(city));
        assertEquals(0,admin.heldExpansionProceeds(city));
        assertFalse(admin.debitExpansion(city, tx, 200));
    }

    @Test void debitAndRefundFlagsSurviveReload() {
        var admin = new KingdomAdministrationData();
        var city = UUID.randomUUID();
        var tx = UUID.randomUUID();
        assertTrue(admin.depositTreasuryReserve(city, 400));
        assertTrue(admin.debitExpansion(city, tx, 100));
        var restored = KingdomAdministrationData.fromTag(admin.save(new CompoundTag()));
        assertEquals(300, restored.treasuryReserve(city));
        assertEquals(100,restored.heldExpansionProceeds(city));
        assertNotNull(restored.expansionDebit(tx));
        assertFalse(restored.expansionDebit(tx).refunded());
        assertTrue(restored.refundExpansion(tx));
        var refunded = KingdomAdministrationData.fromTag(restored.save(new CompoundTag()));
        assertEquals(400, refunded.treasuryReserve(city));
        assertTrue(refunded.expansionDebit(tx).refunded());
        assertTrue(refunded.refundExpansion(tx));
        assertEquals(400, refunded.treasuryReserve(city));
    }

    @Test void insufficientReserveNeverCreatesDebit() {
        var admin = new KingdomAdministrationData();
        var city = UUID.randomUUID();
        var tx = UUID.randomUUID();
        assertTrue(admin.depositTreasuryReserve(city, 50));
        assertFalse(admin.debitExpansion(city, tx, 100));
        assertNull(admin.expansionDebit(tx));
        assertEquals(50, admin.treasuryReserve(city));
    }
}
