package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real resolver against local profile/worker fixtures without starting Minecraft. */
final class KingdomIdentityNamesTest {
    @Test
    void resolvesNpcOnlineAndCachedOfflineNamesWithoutNetworkLookups(@TempDir Path classes)throws Exception{
        var compiler=ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler,"Identity boundary test requires the JDK used by the build");
        var arguments=new ArrayList<String>();
        arguments.addAll(java.util.List.of("--release","17","-d",classes.toString()));
        try(var sources=Files.walk(Path.of("validation/identity_names_harness/src"))){
            sources.filter(path->path.toString().endsWith(".java"))
                    .sorted().forEach(path->arguments.add(path.toString()));
        }
        arguments.add("src/main/java/ru/projectkingdom/npc/KingdomIdentityNames.java");
        var output=new ByteArrayOutputStream();
        assertEquals(0,compiler.run(null,output,output,arguments.toArray(String[]::new)),output.toString());
        // Isolate the boundary fixtures from the full Forge test classpath.
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            loader.loadClass("ru.projectkingdom.npc.IdentityNamesHarness")
                    .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        }
    }
}
