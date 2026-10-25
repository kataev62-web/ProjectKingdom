package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

final class MunicipalFoodMenuTest {
    @Test void networkSnapshotRoundTripDoesNotTurnMissingInventoryIntoZero() {
        var before=new MunicipalFoodView(true,false,true,50,112,30,15,4,
                3000,0,List.of(1,2,3,4,5),
                List.of(new MunicipalFoodView.Offer("tfc:food/bread",8)),
                List.of("tfc:food/bread ×2 — IN_TRANSIT 0/2"),"Недоступен один склад");
        FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());
        MunicipalFoodView.write(bytes,before);
        var after=MunicipalFoodView.read(bytes);
        assertEquals(before,after);
        assertFalse(after.complete());
        assertEquals(1,after.offers().size());
    }
    @Test void emptySnapshotHasSafeFiveGroupPayload() {
        var before=MunicipalFoodView.empty();
        FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());
        MunicipalFoodView.write(bytes,before);
        var after=MunicipalFoodView.read(bytes);
        assertEquals(5,after.groups().size());
        assertFalse(after.configured());
        assertFalse(after.canConfigure());
    }
    @Test void municipalInterfacesExposeFoodTab() {
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.BANK)
                .contains(AdministrativeMenuLayout.Tab.FOOD));
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.CENTRAL_WAREHOUSE)
                .contains(AdministrativeMenuLayout.Tab.FOOD));
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.TOWN_HALL)
                .contains(AdministrativeMenuLayout.Tab.FOOD));
        assertFalse(AdministrativeMenuLayout.tabs(AdministrativeKind.PRISON)
                .contains(AdministrativeMenuLayout.Tab.FOOD));
    }
    @Test void packetPayloadValidationRejectsExtraOffers() {
        assertThrows(IllegalArgumentException.class,() ->
                new MunicipalFoodView(true,true,true,12,8,30,100,1,500,400,
                        List.of(1,1,1,1,1),
                        List.of(new MunicipalFoodView.Offer("tfc:food/apple",1),
                                new MunicipalFoodView.Offer("tfc:food/apple",1),
                                new MunicipalFoodView.Offer("tfc:food/apple",1),
                                new MunicipalFoodView.Offer("tfc:food/apple",1),
                                new MunicipalFoodView.Offer("tfc:food/apple",1),
                                new MunicipalFoodView.Offer("tfc:food/apple",1)),
                        List.of(),""));
    }
}
