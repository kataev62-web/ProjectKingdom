package ru.projectkingdom.core;

import java.util.Objects;
import java.util.UUID;

/** Persistent player identity created once from the launcher account. */
public record PlayerIdentity(
        IdentityIds.AccountId accountId,
        IdentityIds.CharacterId characterId,
        IdentityIds.DynastyId dynastyId,
        UUID currentSessionId,
        boolean heir,
        int resurrections) {
    public PlayerIdentity {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(dynastyId, "dynastyId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        if (resurrections < 0) throw new IllegalArgumentException("resurrections");
    }

    public static PlayerIdentity create(IdentityIds.AccountId accountId, UUID sessionId) {
        return new PlayerIdentity(accountId, IdentityIds.CharacterId.random(),
                IdentityIds.DynastyId.random(), sessionId, false, 0);
    }

    public PlayerIdentity rebind(UUID sessionId) {
        return new PlayerIdentity(accountId, characterId, dynastyId,
                Objects.requireNonNull(sessionId, "sessionId"), heir, resurrections);
    }

    public PlayerIdentity resurrect() {
        return new PlayerIdentity(accountId, characterId, dynastyId,
                currentSessionId, heir, Math.addExact(resurrections, 1));
    }

    public PlayerIdentity asHeir() {
        return new PlayerIdentity(accountId, characterId, dynastyId, currentSessionId, true, resurrections);
    }
}
