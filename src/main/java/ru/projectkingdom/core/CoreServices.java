package ru.projectkingdom.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Small shared contracts used by later gameplay stages without coupling their implementations. */
public final class CoreServices {
    private CoreServices() {}

    public enum Permission { OPEN_BOOK, VIEW_FINANCES, MANAGE_DYNASTY, MANAGE_STATE }
    public static final class Permissions {
        private final Map<UUID, EnumSet<Permission>> grants = new ConcurrentHashMap<>();
        public void grant(UUID character, Permission permission) { grants.computeIfAbsent(character, ignored -> EnumSet.noneOf(Permission.class)).add(permission); }
        public boolean allows(UUID character, Permission permission) { return grants.getOrDefault(character, EnumSet.noneOf(Permission.class)).contains(permission); }
    }

    public static final class FeatureFlags {
        private final Set<String> enabled = ConcurrentHashMap.newKeySet();
        public void enable(String flag) { enabled.add(flag); }
        public void disable(String flag) { enabled.remove(flag); }
        public boolean enabled(String flag) { return enabled.contains(flag); }
    }

    public record AuditEntry(Instant at, String actor, String action, String detail) {}
    public static final class AuditLog {
        private final List<AuditEntry> entries = Collections.synchronizedList(new ArrayList<>());
        public void record(String actor, String action, String detail) { entries.add(new AuditEntry(Instant.now(), actor, action, detail)); }
        public List<AuditEntry> snapshot() { synchronized (entries) { return List.copyOf(entries); } }
    }

    public static final class Events<T> {
        private final List<Consumer<T>> listeners = new ArrayList<>();
        public void subscribe(Consumer<T> listener) { listeners.add(listener); }
        public int publish(T event) {
            int failures = 0;
            for (Consumer<T> listener : List.copyOf(listeners)) {
                try { listener.accept(event); } catch (RuntimeException ignored) { failures++; }
            }
            return failures;
        }
    }
}
