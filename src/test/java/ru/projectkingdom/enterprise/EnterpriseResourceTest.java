package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class EnterpriseResourceTest {
    @Test void everyPrivateKindHasACompleteDistinctResourceMatrix() throws Exception {
        String language=resource("/assets/kingdomnpc/lang/ru_ru.json");
        for(EnterpriseKind kind:EnterpriseKind.placeableKinds()){
            String id=kind.blockId();
            assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/blockstates/"+id+".json"),id+" blockstate");
            assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/models/block/"+id+".json"),id+" block model");
            assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/models/item/"+id+".json"),id+" item model");
            assertNotNull(getClass().getResourceAsStream("/data/kingdomnpc/recipes/"+id+".json"),id+" recipe");
            try(InputStream stream=getClass().getResourceAsStream("/assets/kingdomnpc/textures/block/"+id+".png")){
                assertNotNull(stream,id+" texture");BufferedImage image=ImageIO.read(stream);assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());
            }
            assertTrue(language.contains("block.kingdomnpc."+id));
        }
        assertTrue(Files.notExists(Path.of("src/main/resources/assets/kingdomnpc/blockstates/warehouse_core.json")));
        assertTrue(Files.notExists(Path.of("src/main/resources/assets/kingdomnpc/blockstates/market_core.json")));
    }
    private String resource(String path)throws Exception{try(InputStream stream=getClass().getResourceAsStream(path)){assertNotNull(stream,path);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}}
}
