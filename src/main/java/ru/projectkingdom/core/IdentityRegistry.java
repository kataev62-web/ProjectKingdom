package ru.projectkingdom.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Enforces exactly one active character for each launcher account. */
public final class IdentityRegistry {
    public enum BindKind { CREATED, RETURNING_SESSION, REBOUND_SESSION }
    public record BindResult(PlayerIdentity identity, BindKind kind) {}

    private final Map<IdentityIds.AccountId, PlayerIdentity> byAccount = new LinkedHashMap<>();
    private final Map<UUID, IdentityIds.AccountId> bySession = new LinkedHashMap<>();

    public synchronized BindResult bind(IdentityIds.AccountId accountId, UUID sessionId) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(sessionId, "sessionId");
        PlayerIdentity prior = byAccount.get(accountId);
        if (prior == null) {
            PlayerIdentity created = PlayerIdentity.create(accountId, sessionId);
            byAccount.put(accountId, created);
            bySession.put(sessionId, accountId);
            return new BindResult(created, BindKind.CREATED);
        }
        if (prior.currentSessionId().equals(sessionId)) {
            bySession.put(sessionId, accountId);
            return new BindResult(prior, BindKind.RETURNING_SESSION);
        }
        bySession.remove(prior.currentSessionId());
        PlayerIdentity rebound = prior.rebind(sessionId);
        byAccount.put(accountId, rebound);
        bySession.put(sessionId, accountId);
        return new BindResult(rebound, BindKind.REBOUND_SESSION);
    }

    public synchronized void restore(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (byAccount.containsKey(identity.accountId())) throw new IllegalStateException("duplicate account");
        IdentityIds.AccountId collision = bySession.get(identity.currentSessionId());
        if (collision != null && !collision.equals(identity.accountId())) throw new IllegalStateException("duplicate session");
        byAccount.put(identity.accountId(), identity);
        bySession.put(identity.currentSessionId(), identity.accountId());
    }

    public synchronized Optional<PlayerIdentity> account(IdentityIds.AccountId id) {
        return Optional.ofNullable(byAccount.get(id));
    }

    public synchronized Optional<PlayerIdentity> session(UUID id) {
        return Optional.ofNullable(bySession.get(id)).map(byAccount::get);
    }

    public synchronized Collection<PlayerIdentity> all() { return ListCopy.of(byAccount.values()); }

    private static final class ListCopy {
        private static <T> Collection<T> of(Collection<T> source) { return java.util.List.copyOf(source); }
    }
}
