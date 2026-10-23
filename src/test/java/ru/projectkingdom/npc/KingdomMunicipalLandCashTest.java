package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class KingdomMunicipalLandCashTest {
    @Test void bookingSurvivesReloadWithFrozenPriceAndBuyer() throws Exception {
        UUID id=UUID.randomUUID(), town=UUID.randomUUID(), buyer=UUID.randomUUID();
        CompoundTag row=new CompoundTag();
        row.putUUID("Id",id);row.putUUID("Town",town);row.putUUID("Buyer",buyer);
        row.putString("Name","Tester");row.putString("Dimension","minecraft:overworld");
        row.putInt("X",42);row.putInt("Z",-9);row.putLong("Price",250);
        row.putLong("Until",System.currentTimeMillis()+60_000);
        row.putString("State","RESERVED");
        ListTag entries=new ListTag();entries.add(row);
        CompoundTag root=new CompoundTag();root.put("Reservations",entries);
        Method loader=KingdomLandReservations.class.getDeclaredMethod("load",CompoundTag.class);
        loader.setAccessible(true);
        var data=(KingdomLandReservations)loader.invoke(null,root);
        var res=data.buyerReservation(town,buyer,System.currentTimeMillis());
        assertNotNull(res);assertEquals(250,res.price());assertEquals(42,res.x());
        assertEquals(-9,res.z());
        var stored=data.save(new CompoundTag());
        var reloaded=(KingdomLandReservations)loader.invoke(null,stored);
        assertEquals(res,reloaded.buyerReservation(town,buyer,System.currentTimeMillis()));
        assertNull(reloaded.buyerReservation(town,UUID.randomUUID(),System.currentTimeMillis()));
        assertNull(reloaded.buyerReservation(town,buyer,Long.MAX_VALUE));
    }
    @Test void uncertainReservationRemainsLockedUntilManualAudit() throws Exception {
        UUID town=UUID.randomUUID(),buyer=UUID.randomUUID();
        CompoundTag row=new CompoundTag();
        row.putUUID("Id",UUID.randomUUID());row.putUUID("Town",town);row.putUUID("Buyer",buyer);
        row.putString("Name","Buyer");row.putString("Dimension","minecraft:overworld");
        row.putInt("X",1);row.putInt("Z",2);row.putLong("Price",100);
        row.putLong("Until",Long.MAX_VALUE);row.putString("State","NEEDS_REVIEW");
        var tags=new ListTag();tags.add(row);
        var root=new CompoundTag();root.put("Reservations",tags);
        Method loader=KingdomLandReservations.class.getDeclaredMethod("load",CompoundTag.class);
        loader.setAccessible(true);
        var data=(KingdomLandReservations)loader.invoke(null,root);
        assertNull(data.buyerReservation(town,buyer,System.currentTimeMillis()));
        assertEquals(KingdomLandReservations.State.NEEDS_REVIEW,
                data.reservedForTown(town,System.currentTimeMillis()).get(0).state());
    }
    @Test void allThreeCashFlowsReferenceRealProtectedVaults() throws Exception {
        String bank=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomBankData.java"));
        String enterprise=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomEnterpriseData.java"));
        String admin=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java"));
        String cash=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomPhysicalCash.java"));
        assertTrue(bank.contains("KingdomPhysicalCash.deposit("));
        assertTrue(bank.contains("KingdomPhysicalCash.withdraw("));
        assertTrue(enterprise.contains("KingdomPhysicalCash.takeForNpc("));
        assertTrue(enterprise.contains("KingdomPhysicalCash.deposit("));
        assertTrue(admin.contains("KingdomPhysicalCash.townVault("));
        assertTrue(cash.contains("planWithdrawal("));
        assertTrue(cash.contains("planDeposit("));
    }
}
