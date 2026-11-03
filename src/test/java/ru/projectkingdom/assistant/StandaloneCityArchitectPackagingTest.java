package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StandaloneCityArchitectPackagingTest {
    private static final Path BASE_JAR = Path.of(
            "build/libs/kingdomnpc-0.1.0-stage0-voice3.3-ai1.jar");
    private static final Path ADDON_JAR = Path.of(
            "build/libs/kingdom-city-architect-0.1.0-ai1.jar");

    @Test void buildsIndependentAddonWithMandatoryBaseDependency() throws IOException {
        assertTrue(Files.isRegularFile(BASE_JAR), "base JAR must be built before packaging test");
        assertTrue(Files.isRegularFile(ADDON_JAR), "standalone add-on JAR is missing");

        Set<String> baseEntries = entries(BASE_JAR);
        Set<String> addonEntries = entries(ADDON_JAR);
        assertFalse(baseEntries.stream().anyMatch(
                name -> name.startsWith("ru/projectkingdom/cityarchitect/")));
        assertTrue(addonEntries.contains(
                "ru/projectkingdom/cityarchitect/KingdomCityArchitectMod.class"));
        assertFalse(addonEntries.contains("ru/projectkingdom/npc/KingdomNpcMod.class"));

        String metadata = textEntry(ADDON_JAR, "META-INF/mods.toml");
        assertTrue(metadata.contains("modId=\"kingdom_city_architect\""));
        assertTrue(metadata.contains("[[dependencies.kingdom_city_architect]]"));
        assertTrue(metadata.contains("modId=\"kingdomnpc\""));
        assertTrue(metadata.contains("mandatory=true"));
        assertTrue(metadata.contains("side=\"BOTH\""));
    }

    private static Set<String> entries(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> values = zip.entries();
            while (values.hasMoreElements()) names.add(values.nextElement().getName());
            return names;
        }
    }

    private static String textEntry(Path archive, String name) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            assertTrue(entry != null, name + " is missing");
            return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
