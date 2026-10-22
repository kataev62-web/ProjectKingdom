import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AdministrationCoreSourceContractTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path registry=Path.of("src/main/java/ru/projectkingdom/administration/KingdomAdministrationRegistry.java");
        Path block=Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreBlock.java");
        check(Files.exists(registry)&&Files.exists(block),"administrative core implementation missing");
        String r=Files.readString(registry),b=Files.readString(block);
        for(String id:List.of("town_hall_core","treasury_core","bank_core","central_warehouse_core","city_market_core","land_office_core","court_core","prison_core","guard_administration_core"))
            check(r.contains("\""+id+"\""),id);
        check(b.contains("townAt"),"town claim gate missing");
        check(b.contains("hasChunkAt"),"loaded chunk proof missing");
        check(!b.contains("getChunk("),"must not force load");
        System.out.println("PASS administration core source contract");
    }
}
