package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AdministrationResourceTest {
    private static final String[] IDS={"town_hall_core","treasury_core","bank_core","central_warehouse_core","city_market_core","land_office_core","court_core","prison_core","guard_administration_core"};
    @Test void everyAdministrativeBlockHasCompleteDistinctResources() throws Exception {
        Path assets=Path.of("src/main/resources/assets/kingdomnpc"),data=Path.of("src/main/resources/data/kingdomnpc/recipes");
        Set<String> hashes=new HashSet<>();String lang=Files.readString(assets.resolve("lang/ru_ru.json"));
        for(String id:IDS){
            Path state=assets.resolve("blockstates/"+id+".json"),block=assets.resolve("models/block/"+id+".json"),item=assets.resolve("models/item/"+id+".json");
            Path texture=assets.resolve("textures/block/"+id+".png"),recipe=data.resolve(id+".json");
            assertTrue(Files.isRegularFile(state),state.toString());assertTrue(Files.isRegularFile(block),block.toString());
            assertTrue(Files.isRegularFile(item),item.toString());assertTrue(Files.isRegularFile(texture),texture.toString());assertTrue(Files.isRegularFile(recipe),recipe.toString());
            assertTrue(Files.readString(state).contains("kingdomnpc:block/"+id));assertTrue(Files.readString(block).contains("kingdomnpc:block/"+id));
            assertTrue(Files.readString(item).contains("kingdomnpc:block/"+id));assertTrue(Files.readString(recipe).contains("kingdomnpc:"+id));
            assertTrue(lang.contains("\"block.kingdomnpc."+id+"\""));BufferedImage image=ImageIO.read(texture.toFile());
            assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());hashes.add(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(texture))));
        }
        assertEquals(IDS.length,hashes.size());
        for(AdministrativeKind kind:AdministrativeKind.values())for(ru.projectkingdom.enterprise.EnterpriseKind enterprise:ru.projectkingdom.enterprise.EnterpriseKind.values())
            assertNotEquals(kind.id()+"_core",enterprise.id()+"_core");
    }
}
