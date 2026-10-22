import java.nio.file.*;

public final class EconomyVisualContractTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Path resources=Path.of("src/main/resources");
        for(String id:new String[]{"copper_coin","silver_coin","gold_coin","wallet"}) {
            check(Files.isRegularFile(resources.resolve("assets/kingdomnpc/textures/item/"+id+".png")),id+" texture");
        }
        check(Files.isRegularFile(resources.resolve("assets/kingdomnpc/font/readable.json")),"font json");
        check(Files.isRegularFile(resources.resolve("assets/kingdomnpc/font/dejavu_sans.ttf")),"font file");
        String book=Files.readString(Path.of("src/main/java/ru/projectkingdom/core/KingdomBookScreen.java"));
        String purse=Files.readString(Path.of("src/main/java/ru/projectkingdom/economy/MoneyPurseScreen.java"));
        check(book.contains("KingdomFonts.readable"),"book scoped font");
        check(purse.contains("KingdomFonts.readable"),"purse scoped font");
        System.out.println("PASS original economy textures and scoped readable font contract");
    }
}
