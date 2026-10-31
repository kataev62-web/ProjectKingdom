package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Guards against player UV corruption and missing faces, encountered with old Villager skins. */
final class KingdomPlayerNpcModelTest {
    private static BufferedImage image(String file) throws Exception {
        try (InputStream input = KingdomPlayerNpcModelTest.class.getResourceAsStream(
                "/assets/kingdomnpc/textures/entity/" + file)) {
            assertNotNull(input, "Missing player skin " + file);
            BufferedImage png = ImageIO.read(input);
            assertNotNull(png, "Damaged PNG " + file);
            assertEquals(64, png.getWidth());
            assertEquals(64, png.getHeight());
            return png;
        }
    }

    @Test void allPlayerSkinUVsContainOpaqueHeadAndBody() throws Exception {
        for (String gender : new String[]{"female", "male"}) {
            for (int i = 0; i < 4; i++) {
                String file = "player_" + gender + "_" + i + ".png";
                var img = image(file);
                // Correct PlayerModel UVs (NOT VillagerModel UVs).
                int[][] areas = {{8,8,16,16}, {20,20,28,32},
                                 {44,20,48,32}, {36,52,40,64},
                                 {4,20,8,32}, {20,52,24,64}};
                for (int[] a : areas) {
                    for (int y = a[1]; y < a[3]; y++) {
                        for (int x = a[0]; x < a[2]; x++) {
                            int color = img.getRGB(x,y);
                            assertEquals(255, (color >>> 24) & 255, file + " transparent base UV @ " + x + "," + y);
                            int r=(color>>>16)&255, g=(color>>>8)&255, b=color&255;
                            assertFalse(r>248 && g<8 && b>248, file + " missing-texture magenta");
                        }
                    }
                }
            }
        }
    }

    @Test void persistedGenderSelectsPlayerModelAppearance() {
        var worker = new KingdomNpcData.Worker(14, java.util.UUID.randomUUID(),
                "Human", "minecraft:overworld", 0);
        worker.gender = "unspecified";
        assertEquals(1, KingdomFemaleOutfitSync.appearance(worker));
        worker.gender = "male";
        assertEquals(1, KingdomFemaleOutfitSync.appearance(worker));
        worker.gender = "female";
        worker.ageYears = 26;
        assertEquals(2, KingdomFemaleOutfitSync.appearance(worker));
        worker.ageYears = 15;
        assertEquals(3, KingdomFemaleOutfitSync.appearance(worker));
        assertEquals(0, KingdomFemaleOutfitSync.appearance(null));
    }
}
