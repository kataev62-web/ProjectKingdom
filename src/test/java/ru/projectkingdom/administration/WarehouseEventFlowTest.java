package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Behavioral tests that exercise the full Forge event flow:
 * PlayerInteractEvent.RightClickBlock -> (simulated KingdomNpcMod logic)
 * -> KingdomCentralWarehouseRooms.select -> visual packets -> SavedData
 * 
 * Note: This test simulates the KingdomNpcMod.selectWarehouseCorners logic
 * without compiling KingdomNpcMod, to avoid requiring full Forge stubs.
 * The real KingdomNpcMod integration is verified in CI with full Forge runtime.
 */
final class WarehouseEventFlowTest {
    @TempDir static Path classes;

    @BeforeAll
    static void compileEventHarness() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Warehouse event test requires the build JDK");
        var arguments = new ArrayList<String>();
        arguments.addAll(java.util.List.of("--release", "17", "-d", classes.toString()));
        try (var sources = Files.walk(Path.of("validation/warehouse_room_harness/src"))) {
            sources.filter(p -> p.toString().endsWith(".java"))
                .sorted().forEach(p -> arguments.add(p.toString()));
        }
        arguments.add("src/main/java/ru/projectkingdom/administration/KingdomCentralWarehouseRooms.java");
        var output = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, output, output, arguments.toArray(String[]::new)), output.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "event_two_clicks",
        "event_item_in_hand_preserves_first",
        "event_retry_after_error",
        "event_diagonal_height",
        "event_no_storage_allowed",
        "event_wrong_dimension",
        "event_no_rights",
        "event_too_far",
        "event_saveddata_roundtrip",
        "event_cancel_clears_session",
        "event_conflict_enterprise_selection",
        "event_conflict_structure_copy",
        "event_conflict_bank_vault",
        "event_visual_packets_sent"
    })
    void selectsAndValidatesViaEventFlow(String scenario) throws Exception {
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.loadClass("ru.projectkingdom.administration.WarehouseEventHarness")
                .getMethod("main", String[].class).invoke(null, (Object) new String[]{scenario});
        }
    }
}