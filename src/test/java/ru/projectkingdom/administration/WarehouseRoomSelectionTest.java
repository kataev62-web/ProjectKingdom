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

/** Runs production begin/select against isolated world, permission and storage fixtures. */
final class WarehouseRoomSelectionTest {
    @TempDir static Path classes;
    @BeforeAll static void compileSelection() throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler,"Warehouse boundary test requires the build JDK");
        var arguments=new ArrayList<String>();
        arguments.addAll(java.util.List.of("--release","17","-d",classes.toString()));
        try(var sources=Files.walk(Path.of("validation/warehouse_room_harness/src"))){
            sources.filter(p->p.toString().endsWith(".java"))
                .sorted().forEach(p->arguments.add(p.toString()));
        }
        arguments.add("src/main/java/ru/projectkingdom/administration/KingdomCentralWarehouseRooms.java");
        var output=new ByteArrayOutputStream();
        assertEquals(0,compiler.run(null,output,output,arguments.toArray(String[]::new)),output.toString());
    }
    @ParameterizedTest
    @ValueSource(strings={"floor","diagonal","retry","height","no_storage","core_inside","retry_far","rights","dimension","upper_limit"})
    void selectsAndValidatesRealRoom(String scenario) throws Exception {
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            loader.loadClass("ru.projectkingdom.administration.WarehouseRoomHarness")
                .getMethod("main",String[].class).invoke(null,(Object)new String[]{scenario});
        }
    }
}
