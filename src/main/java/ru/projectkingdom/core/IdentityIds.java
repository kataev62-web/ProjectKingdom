package ru.projectkingdom.core;

import java.util.Objects;
import java.util.UUID;

/** Stable domain identifiers. A Minecraft session UUID is deliberately not one of them. */
public final class IdentityIds {
    private IdentityIds() {}

    public record AccountId(String value) {
        public AccountId {
            value = Objects.requireNonNull(value, "value").trim();
            if (value.isEmpty() || value.length() > 128) throw new IllegalArgumentException("invalid account id");
        }
    }

    public record CharacterId(UUID value) {
        public CharacterId { Objects.requireNonNull(value, "value"); }
        public static CharacterId random() { return new CharacterId(UUID.randomUUID()); }
    }

    public record DynastyId(UUID value) {
        public DynastyId { Objects.requireNonNull(value, "value"); }
        public static DynastyId random() { return new DynastyId(UUID.randomUUID()); }
    }
}
