package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Read-only checkpoint eligibility. A matching receipt is not evidence that
 * Minecraft's SavedData and chunk writes reached durable storage together.
 * In particular, this class must never release trade quarantine.
 */
public final class TradeCheckpointGate {
    private final TradeCheckpointLedger ledger;
    public TradeCheckpointGate(TradeCheckpointLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger);
    }
    public boolean candidate(UUID trade, String afterDigest, String worldGeneration, long saveEpoch)
            throws IOException {
        return ledger.matches(trade, afterDigest, worldGeneration, saveEpoch);
    }
}
