# Managed storage integration — partial

Implemented: runtime enrollment from persisted UUID on BlockEntity load; cleanup on removal; fail-closed for unbound and conflicting registrations; lock-aware IItemHandler operations synchronized on the same guard instance.

**Limitations**: runtime lock singleton is not yet shared by TradePhysicalProjectionCoordinator production callers; it is not durable across crashes. No verified ForgeGradle build. No secure player UI / direct inventory mutation access. The storage still lacks recovery quarantine, authorized purchase coordinator integration and full real-game testing. Never expose backing ItemStackHandler. If an item transfer is executed by a mod bypassing this capability, protection is not guaranteed. A binding collision can block automation until resolved. Existing owner binding lacks player permission checks and must not be exposed to untrusted clients.
