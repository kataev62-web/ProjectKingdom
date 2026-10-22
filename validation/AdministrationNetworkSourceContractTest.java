import java.nio.file.Files;
import java.nio.file.Path;

public final class AdministrationNetworkSourceContractTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path action=Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java");
        Path access=Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeAccessService.java");
        check(Files.exists(action)&&Files.exists(access),"secure administration actions missing");
        String a=Files.readString(action),s=Files.readString(access);
        check(a.contains("sender.containerMenu instanceof AdministrativeCoreMenu"),"open-menu validation missing");
        check(a.contains("menu.stillValid(sender)"),"core validity recheck missing");
        check(s.contains("hasChunkAt(pos)"),"loaded chunk guard missing");
        check(s.contains("townAt(level,pos)"),"exact town check missing");
        check(!s.contains("getChunk("),"forced chunk load forbidden");
        String data=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java"));
        check(data.contains("root.put(\"Containers\""),"container binding save missing");
        System.out.println("PASS administration network source contract");
    }
}
