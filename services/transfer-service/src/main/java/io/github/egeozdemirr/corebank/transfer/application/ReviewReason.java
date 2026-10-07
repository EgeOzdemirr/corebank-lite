package io.github.egeozdemirr.corebank.transfer.application;

/** Why a transfer was put on the review queue. */
public enum ReviewReason {
    /**
     * account-service already holds a posting under this transfer's id with different content. Retrying cannot
     * help: the transfer stays APPROVED and is never retried automatically (ADR-0004, ADR-0005).
     */
    POSTING_ID_CONFLICT
}
