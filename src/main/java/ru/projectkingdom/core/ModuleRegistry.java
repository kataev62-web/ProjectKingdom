package ru.projectkingdom.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Starts optional gameplay modules independently; one failure cannot take down the core. */
public final class ModuleRegistry {
    @FunctionalInterface public interface Module { void start() throws Exception; }
    public enum State { REGISTERED, ACTIVE, FAILED, DISABLED }
    public record Failure(String module, String message) {}

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, State> states = new LinkedHashMap<>();
    private final List<Failure> failures = new ArrayList<>();

    public ModuleRegistry register(String name, Module module, boolean enabled) {
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty() || modules.containsKey(name)) throw new IllegalArgumentException("module name");
        modules.put(name, Objects.requireNonNull(module, "module"));
        states.put(name, enabled ? State.REGISTERED : State.DISABLED);
        return this;
    }

    public void startAll() {
        modules.forEach((name, module) -> {
            if (states.get(name) != State.REGISTERED) return;
            try {
                module.start();
                states.put(name, State.ACTIVE);
            } catch (Exception failure) {
                states.put(name, State.FAILED);
                failures.add(new Failure(name, failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            }
        });
    }

    public State state(String name) { return states.get(name); }
    public List<Failure> failures() { return Collections.unmodifiableList(failures); }
}
