package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.administration.AdministrativeAccessService;

final class EnterpriseBindingRussianMessageTest {
    @Test void everyEnterpriseStatusExplainsTheProblemInRussian(){
        for(var status:EnterpriseAccessService.Status.values()){
            String name=EnterpriseAccessService.label(status);
            String hint=EnterpriseAccessService.explanation(status);
            assertNotNull(name);assertNotNull(hint);
            assertFalse(name.isBlank());assertFalse(hint.isBlank());
            assertFalse(name.equals(status.name()));
            assertTrue(hint.matches("(?s).*[\\p{IsCyrillic}].*"),status.name());
        }
        assertTrue(EnterpriseAccessService.explanation(
                EnterpriseAccessService.Status.INVALID_CONTAINER).contains("сундук"));
        assertTrue(EnterpriseAccessService.label(
                EnterpriseAccessService.Status.BOUND_AVAILABLE).contains("Подключено"));
        assertTrue(EnterpriseAccessService.explanation(
                EnterpriseAccessService.Status.ALREADY_ASSIGNED).contains("отдельный"));
    }
    @Test void everyMunicipalStatusHasActionableRussianAdvice(){
        for(var status:AdministrativeAccessService.Status.values()){
            String message=AdministrativeAccessService.explanation(status);
            assertFalse(message.isBlank());
            assertTrue(message.matches("(?s).*[\\p{IsCyrillic}].*"));
        }
    }
    @Test void interfaceNeverLeaksTechnicalStatusCodes() throws Exception {
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreMenu.java"));
        String mod=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        assertTrue(menu.contains("EnterpriseAccessService.display("));
        assertTrue(mod.contains("EnterpriseAccessService.explanation(status)"));
        assertTrue(mod.contains("AdministrativeAccessService.explanation(status)"));
        assertFalse(menu.contains(".status().name()"));
    }
}
