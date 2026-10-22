import java.nio.file.Files;
import java.nio.file.Path;

public final class AdministrationMenuSourceContractTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path layout=Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeMenuLayout.java");
        Path menu=Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreMenu.java");
        Path screen=Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreScreen.java");
        check(Files.exists(layout)&&Files.exists(menu)&&Files.exists(screen),"administrative menu implementation missing");
        String m=Files.readString(menu),s=Files.readString(screen);
        check(m.contains("hasChunkAt(corePos)"),"loaded core guard missing");
        check(m.contains("data.core(townId,kind)"),"active binding guard missing");
        check(s.contains("KingdomFonts.readable"),"scoped readable font missing");
        System.out.println("PASS administration menu source contract");
    }
}
