package ru.projectkingdom.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Stage1ClientRegressionTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/" + relative));
    }

    @Test void recipesAreAwardedByTheServerWhenAPlayerLogsIn() throws Exception {
        String guardian = source("core/KingdomBookGuardian.java");
        assertTrue(guardian.contains("awardRecipes"),
                "recipe visibility must not depend only on a client advancement refresh");
        assertTrue(guardian.contains("KingdomRecipeUnlock.all"),
                "the server must use one tested Kingdom recipe catalog");
    }

    @Test void pendingBindingCannotSwallowNormalBlockPlacement() throws Exception {
        String mod = source("npc/KingdomNpcMod.java");
        assertTrue(mod.contains("BindingInteractionPolicy.allowsPlacement(event.getItemStack())"),
                "a held BlockItem must cancel stale binding and continue normal placement");
        assertTrue(mod.contains("EnterpriseAccessService.cancel(player.getUUID())"));
        assertTrue(mod.contains("AdministrativeAccessService.cancel(player.getUUID())"));
    }

    @Test void purseUsesPhysicalCoinPresentationInsteadOfAPlainAccountingForm() throws Exception {
        String screen = source("economy/MoneyPurseScreen.java");
        assertTrue(screen.contains("graphics.renderItem(new ItemStack(KingdomCurrency.COPPER.get())"));
        assertTrue(screen.contains("graphics.renderItem(new ItemStack(KingdomCurrency.SILVER.get())"));
        assertTrue(screen.contains("graphics.renderItem(new ItemStack(KingdomCurrency.GOLD.get())"));
        assertTrue(screen.contains("Заполнено"));
        assertFalse(screen.contains("ДЕНЕЖНЫЙ МЕШОК"),
                "large all-caps labels are harder to read with Minecraft's Cyrillic font");
    }

    @Test void customScreensUseReadableIconTabsAndMixedCaseHeadings() throws Exception {
        String enterprise = source("enterprise/EnterpriseCoreScreen.java");
        String administration = source("administration/AdministrativeCoreScreen.java");
        String book = source("core/KingdomBookScreen.java");
        assertTrue(enterprise.contains("ParchmentIconButton"));
        assertTrue(administration.contains("ParchmentIconButton"));
        assertFalse(enterprise.contains("title().toUpperCase"));
        assertFalse(administration.contains("russianName().toUpperCase"));
        assertFalse(book.contains("text.toUpperCase"));
    }

    @Test void everyRegisteredUtilityBlockHasSurvivalResources() throws Exception {
        String unlock = Files.readString(Path.of(
                "src/main/resources/data/kingdomnpc/advancements/recipes/unlock_all.json"));
        for (String id : new String[]{"managed_storage", "demand_board"}) {
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/kingdomnpc/blockstates/" + id + ".json")), id);
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/kingdomnpc/models/block/" + id + ".json")), id);
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/kingdomnpc/models/item/" + id + ".json")), id);
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/data/kingdomnpc/recipes/" + id + ".json")), id);
            assertTrue(unlock.contains("kingdomnpc:" + id), id + " is not unlocked");
        }
    }
}
