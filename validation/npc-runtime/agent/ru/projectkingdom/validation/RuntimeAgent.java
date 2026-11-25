package ru.projectkingdom.validation;

import java.lang.instrument.Instrumentation;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** No Minecraft linkage until Forge's transforming class loader is available. */
public final class RuntimeAgent {
    public static void premain(String argument, Instrumentation instrumentation) {
        if (!Boolean.getBoolean("npc.runtime.enabled"))
            throw new IllegalStateException("Explicit -Dnpc.runtime.enabled=true required");
        Thread bootstrap = new Thread(() -> {
            try {
                long deadline = System.nanoTime() + 360_000_000_000L;
                while (System.nanoTime() < deadline) {
                    for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                        if (!loaded.getName().equals("net.minecraftforge.server.ServerLifecycleHooks")) continue;
                        Object server = loaded.getMethod("getCurrentServer").invoke(null);
                        if (server == null) continue;
                        // Keep this loader alive for tick callbacks until the process exits.
                        URLClassLoader helper = new URLClassLoader(
                                new java.net.URL[]{Path.of(argument).toUri().toURL()}, loaded.getClassLoader());
                        String probe = System.getProperty("npc.runtime.probeClass", "ru.projectkingdom.validation.RuntimeProbe");
                        if (!probe.equals("ru.projectkingdom.validation.RuntimeProbe")
                                && !probe.equals("ru.projectkingdom.validation.AutonomousMineProbe")
                                && !probe.equals("ru.projectkingdom.validation.CompletionResumeProbe"))
                            throw new IllegalArgumentException("Unknown validation probe: " + probe);
                        Class.forName(probe, true, helper)
                                .getMethod("boot", Object.class).invoke(null, server);
                        return;
                    }
                    Thread.sleep(250);
                }
                throw new IllegalStateException("Forge server did not become available in 360 seconds");
            } catch (Throwable failure) {
                failure.printStackTrace();
                Runtime.getRuntime().halt(31);
            }
        }, "npc-runtime-validation-bootstrap");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }
}
