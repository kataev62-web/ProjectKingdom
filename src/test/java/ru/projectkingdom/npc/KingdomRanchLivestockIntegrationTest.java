package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomRanchLivestockIntegrationTest {
    private static String source(String name)throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/"+name));
    }
    @Test void penReportsAllAnimalsAndDistinguishesTfcFromOthers(){
        var status=new KingdomLivestockWork.PenStatus(8,5,2,1,0,1,true,true);
        assertEquals(8,status.animals());
        assertEquals(5,status.tfcAnimals());
        assertEquals(3,status.otherAnimals());
        assertTrue(status.areaLoaded());
        assertTrue(status.tfcAvailable());
    }
    @Test void oldStatusConstructorsStayCompatible(){
        var status=new KingdomLivestockWork.PenStatus(4,2,1);
        assertEquals(4,status.animals());
        assertEquals(0,status.otherAnimals());
        assertEquals(2,status.hungry());
    }
    @Test void noSilentEmptyHerdWhenChunksOrTfcUnavailable()throws Exception{
        var work=source("KingdomLivestockWork.java");
        var menu=source("KingdomRanchPenMenu.java");
        assertTrue(work.contains("if(!areaLoaded(level,pen))"));
        assertTrue(work.contains("level.hasChunk(cx,cz)"));
        assertTrue(work.contains("count++;"));
        assertTrue(work.contains("if(!tfcReady||!tfcAnimal.isInstance(animal))continue;"));
        assertTrue(menu.contains("Животные: НЕИЗВЕСТНО"));
        assertTrue(menu.contains("Не все чанки загона загружены"));
        assertTrue(menu.contains("Другие животные: "));
        assertTrue(menu.contains("TFC-обработчик недоступен"));
        assertTrue(menu.contains("getOrCreateTagElement(\"display\")"));
    }
    @Test void rancherAutoFeedIsOwnerOptInAndManualFeedingPreserved()throws Exception{
        var work=source("KingdomLivestockWork.java");
        var menu=source("KingdomRanchPenMenu.java");
        var policy=source("KingdomRanchPolicyData.java");
        assertTrue(work.contains("if(policy.autoFeed()&&hungry(animal)"));
        assertTrue(menu.contains("policy.toggleAutoFeed(player,farm"));
        assertTrue(menu.contains("Shift + ПКМ с подходящим кормом"));
        assertTrue(policy.contains("!old.autoFeed()"));
        assertTrue(policy.contains("row.getBoolean(\"AutoFeed\")"));
        assertFalse(source("KingdomNpcMod.java").contains("PlayerInteractEvent.EntityInteract event) {\n        KingdomLivestockWork"));
    }
    @Test void modernCityTitleIsCheckedAheadOfLegacyLandClaim()throws Exception{
        var pens=source("KingdomRanchPenData.java");
        var selection=source("KingdomFarmSelection.java");
        assertTrue(pens.contains("if(civics.plot(level,pos)!=null)"));
        assertTrue(pens.contains("KingdomCivicsData.Action.BUILD"));
        assertTrue(selection.contains("KingdomRanchPenData.canRegisterAt"));
        assertTrue(pens.contains("if(!canRegisterAt(level,soil,player.getUUID()))return false"));
        assertTrue(pens.contains("if(!canRegisterAt(level,soil,npcOwner))return false"));
    }
}
