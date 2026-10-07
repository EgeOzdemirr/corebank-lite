package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Something that happened to a transfer. Every event carries the full order and the checker, because the published
 * contracts do (aml-monitor needs both accounts, the beneficiary, the amount, the channel and the maker-checker pair).
 */
public sealed interface TransferEvent permits TransferRequested, TransferApproved, TransferPosted, TransferFailed {

    TransferId transferId();

    TransferOrder order();

    /** Empty below the approval threshold (approved automatically) and before an approval. */
    Optional<UserId> checker();

    Instant occurredAt();
}
