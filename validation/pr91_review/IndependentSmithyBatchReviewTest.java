package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

class IndependentSmithyBatchReviewTest {
    @Test void nativeTwoRodBatchMustBeSupportedOrRejectedBeforeWork()throws Exception{
        String path=System.getenv("TFC_REVIEW_JAR");
        assertNotNull(path,"Provide official TFC 3.2.26 JAR, not a hand-written recipe");
        int count;
        try(var jar=new ZipFile(path)){
            var row=jar.getEntry("data/tfc/recipes/anvil/black_bronze_rod.json");
            assertNotNull(row);
            var json=JsonParser.parseString(new String(jar.getInputStream(row).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            count=json.getAsJsonObject("result").get("count").getAsInt();
        }
        assertEquals(2,count);
        var orders=new KingdomSmithyOrdersData();
        var firm=UUID.randomUUID();var worker=UUID.randomUUID();
        boolean accepted=orders.create(firm,worker,"tfc:anvil/black_bronze_rod","tfc:metal/ingot/black_bronze",1);
        if(!accepted)return; // A pre-work quantity check would be safe.
        assertTrue(orders.working(firm,worker));
        boolean recorded=orders.ready(firm,worker,"tfc:metal/rod/black_bronze",count);
        if(!recorded){
            assertEquals(KingdomSmithyOrdersData.Phase.WORKING,orders.order(firm).phase());
            assertFalse(orders.create(firm,worker,"tfc:anvil/black_bronze_rod","tfc:metal/ingot/black_bronze",2));
        }
        assertTrue(recorded,
            "Accepted quantity=1 strands native two-rod output after forging: Order remains WORKING, cannot be replaced");
    }
}
