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
 * PlayerInteractEvent.RightClickBlock -> KingdomNpcMod.selectWarehouseCorners (REAL HANDLER)
 * -> KingdomCentralWarehouseRooms.select -> visual packets -> SavedData
 * 
 * This test calls the REAL KingdomNpcMod.selectWarehouseCorners handler
 * and verifies:
 * - Visual selection packet calls (KingdomVolumeSelectionPacket.send)
 * - Two-corner registration
 * - Marking cancellation (KingdomCancelVolumeSelectionPacket)
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
        // Compile main source files needed for the test
        arguments.add("src/main/java/ru/projectkingdom/administration/KingdomCentralWarehouseRooms.java");
        // Use test-specific KingdomNpcModTest that has only the selectWarehouseCorners handler
        arguments.add("validation/warehouse_room_harness/src/ru/projectkingdom/npc/KingdomNpcModTest.java");
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