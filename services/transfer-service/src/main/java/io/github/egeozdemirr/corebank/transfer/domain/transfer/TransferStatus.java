package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a transfer. The happy path is CREATED -> APPROVED -> POSTED, with PENDING_APPROVAL in between when
 * the amount is above the approval threshold. FAILED and REVERSED are the error paths and are final.
 */
public enum TransferStatus {
    CREATED,
    PENDING_APPROVAL,
    APPROVED,
    POSTED,
    FAILED,
    REVERSED;

    /**
     * The only place that defines the state machine. FAILED is reachable only after the transfer was recorded:
     * requests that fail validation never become transfers. REVERSED undoes a posted transfer (saga, week 3).
     */
    private static final Map<TransferStatus, Set<TransferStatus>> NEXT_STATUSES = Map.of(
            CREATED, Set.of(PENDING_APPROVAL, APPROVED),
            PENDING_APPROVAL, Set.of(APPROVED, FAILED),
            APPROVED, Set.of(POSTED, FAILED),
            POSTED, Set.of(REVERSED),
            FAILED, Set.of(),
            REVERSED, Set.of());

    public boolean canMoveTo(TransferStatus target) {
        return NEXT_STATUSES.get(this).contains(target);
    }
}
