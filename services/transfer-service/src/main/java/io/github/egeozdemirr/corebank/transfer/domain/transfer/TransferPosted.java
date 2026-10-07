package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Both ledger legs were posted in account-service under the posting id {@code transferId} (ADR-0004). */
public record TransferPosted(TransferId transferId, TransferOrder order, Optional<UserId> checker,
                             Instant postedAt) implements TransferEvent {

    public TransferPosted {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(checker, "checker");
        Objects.requireNonNull(postedAt, "postedAt");
    }

    @Override
    public Instant occurredAt() {
        return postedAt;
    }
}
