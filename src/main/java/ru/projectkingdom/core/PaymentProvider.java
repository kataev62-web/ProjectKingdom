package ru.projectkingdom.core;

/** Explicit boundary for real-money top-ups. It remains disabled until the final provider stage. */
public interface PaymentProvider {
    record TopUpResult(boolean accepted, String reference, String reason) {}
    TopUpResult requestTopUp(IdentityIds.AccountId accountId, long minorUnits, String currency);

    static PaymentProvider disabled() {
        return (accountId, minorUnits, currency) -> new TopUpResult(false, "", "provider_not_configured");
    }
}
