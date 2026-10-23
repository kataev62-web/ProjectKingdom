package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KingdomExpansionQuoteTest {
    private final UUID city = UUID.randomUUID();
    private static final String DIM = "minecraft:overworld";
    private KingdomExpansionPlans.Cell cell(int x, int z) {
        return new KingdomExpansionPlans.Cell(DIM, x, z);
    }
    private KingdomCivicsData.Plot plot(int x, int z, UUID owner) {
        return new KingdomCivicsData.Plot(DIM, x, z, owner, null, KingdomCivicsData.Zone.PUBLIC, false);
    }
    @Test void adjacentBatchIsAffordableOnlyWithActualTreasury() {
        var land = List.of(plot(0, 0, city));
        assertEquals(KingdomExpansionQuote.Status.READY,
            KingdomExpansionQuote.evaluate(city, DIM, List.of(cell(1, 0), cell(2, 0)), land, 200).status());
        assertEquals(KingdomExpansionQuote.Status.INSUFFICIENT_TREASURY,
            KingdomExpansionQuote.evaluate(city, DIM, List.of(cell(1, 0), cell(2, 0)), land, 199).status());
    }
    @Test void rejectsDisconnectedOrForeignClaims() {
        var land = List.of(plot(0, 0, city), plot(1, 0, UUID.randomUUID()));
        assertEquals(KingdomExpansionQuote.Status.CONFLICT,
            KingdomExpansionQuote.evaluate(city, DIM, List.of(cell(1, 0)), land, 100).status());
        assertEquals(KingdomExpansionQuote.Status.DISCONNECTED,
            KingdomExpansionQuote.evaluate(city, DIM, List.of(cell(6, 6)), land, 100).status());
    }
    @Test void cannotPriceCrossDimensionOrDuplicatePlans() {
        var land = List.of(plot(0, 0, city));
        assertEquals(KingdomExpansionQuote.Status.CONFLICT,
            KingdomExpansionQuote.evaluate(city, DIM, List.of(cell(1, 0), cell(1, 0)), land, 1000).status());
        assertEquals(KingdomExpansionQuote.Status.CONFLICT,
            KingdomExpansionQuote.evaluate(city, DIM,
                List.of(new KingdomExpansionPlans.Cell("minecraft:the_nether", 1, 0)), land, 1000).status());
    }
}
