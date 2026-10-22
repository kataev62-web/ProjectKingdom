import java.nio.file.Files;
import java.nio.file.Path;

public final class AdministrationPersistenceSourceContractTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path path=Path.of("src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java");
        check(Files.exists(path),"KingdomAdministrationData missing");
        String source=Files.readString(path);
        check(source.contains("DATA_VERSION=2"),"versioned migration missing");
        check(source.contains("MAX_LOG_ENTRIES=500"),"bounded log missing");
        check(source.contains("migrateFounder"),"founder migration missing");
        check(source.contains("activateCore"),"core lifecycle missing");
        check(source.contains("ListTag"),"NBT persistence missing");
        System.out.println("PASS administration persistence source contract");
    }
}
