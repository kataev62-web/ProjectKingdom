package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class KingdomMunicipalFoodReservesTest {
    private static KingdomMunicipalFoodReserves.Policy policy() {
        return new KingdomMunicipalFoodReserves.Policy(10,2,50,96,24,24,8);
    }

    @Test void preparationWindowRaisesTargetBeforeWinter() {
        var policy=policy();
        assertEquals(8,KingdomMunicipalFoodReserves.coverageDays(policy,25));
        assertEquals(56,KingdomMunicipalFoodReserves.coverageDays(policy,26));
        assertEquals(33,KingdomMunicipalFoodReserves.coverageDays(policy,49));
        assertEquals(32,KingdomMunicipalFoodReserves.coverageDays(policy,50));
        assertEquals(9,KingdomMunicipalFoodReserves.coverageDays(policy,73));
        assertEquals(8,KingdomMunicipalFoodReserves.coverageDays(policy,74));
        assertEquals(56,KingdomMunicipalFoodReserves.coverageDays(policy,122));
        assertEquals(1120,KingdomMunicipalFoodReserves.target(policy,56));
        assertEquals(320,KingdomMunicipalFoodReserves.shortage(1120,800));
    }

    @Test void totalIncludesOnlyActualEdiblePortionsAndNoCoins() {
        assertEquals(0,KingdomMunicipalFoodReserves.shortage(100,100));
        assertEquals(1,KingdomMunicipalFoodReserves.shortage(101,100));
        assertEquals(Integer.MAX_VALUE,
                KingdomMunicipalFoodReserves.shortage(Long.MAX_VALUE,0));
        var stats=new KingdomMunicipalFoodReserves.Forecast(50,true,2,100,20,
                List.of(20,20,20,20,20),32,200,100);
        assertTrue(stats.shortage());
        var unknown=new KingdomMunicipalFoodReserves.Forecast(-1,false,0,0,0,
                List.of(0,0,0,0,0),0,200,0);
        assertFalse(unknown.shortage());
        assertThrows(IllegalArgumentException.class,() ->
                new KingdomMunicipalFoodReserves.Forecast(0,true,1,1,0,
                        List.of(0,0),10,10,9));
    }

    @Test void tfcConfiguredMonthsCoverWinterAcrossCalendarYearBoundary() {
        var p=KingdomMunicipalFoodReserves.Policy.fromTfcMonth(10,8);
        assertEquals(-8,p.firstWinterDay());
        assertEquals(96,p.yearDays());
        assertEquals(24,p.winterDays());
        assertEquals(3*8,p.preparationDays());
        assertEquals(24,KingdomMunicipalFoodReserves.coverageDays(p,0));
        assertEquals(8,KingdomMunicipalFoodReserves.coverageDays(p,16));
        assertEquals(56,KingdomMunicipalFoodReserves.coverageDays(p,64));
        assertEquals(24,KingdomMunicipalFoodReserves.coverageDays(p,96));
    }

    @Test void policyPersistsButStaleForecastNeverReplays() {
        var state=new KingdomMunicipalFoodReserves();
        UUID town=UUID.randomUUID();
        assertTrue(state.configure(town,policy()));
        var saved=state.save(new CompoundTag());
        var loaded=KingdomMunicipalFoodReserves.load(saved);
        assertEquals(policy(),loaded.policy(town));
        assertNull(loaded.forecast(town));
    }

    @Test void invalidOrUnconfiguredWinterNeverCreatesVirtualFood() {
        assertEquals(0,KingdomMunicipalFoodReserves.coverageDays(policy(),-1));
        assertThrows(IllegalArgumentException.class,() ->
                new KingdomMunicipalFoodReserves.Policy(0,2,50,96,24,24,8));
        assertThrows(IllegalArgumentException.class,() ->
                new KingdomMunicipalFoodReserves.Policy(10,2,50,96,96,24,8));
        assertThrows(IllegalArgumentException.class,() ->
                new KingdomMunicipalFoodReserves.Policy(10,0,50,96,24,24,8));
        assertThrows(IllegalArgumentException.class,() ->
                KingdomMunicipalFoodReserves.target(policy(),-1));
    }
}
