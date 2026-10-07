package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.util.Objects;

/**
 * Everything persistence needs to rebuild a {@link Transfer}. {@code checker} is null until a checker approved the
 * transfer (and stays null below the threshold); {@code failureReason} is null unless the transfer failed.
 */
public record TransferSnapshot(
        TransferId id,
        TransferOrder order,
        boolean approvalRequired,
        TransferStatus status,
        UserId checker,
        TransferTimeline timeline,
        FailureReason failureReason) {

    public TransferSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(timeline, "timeline");
    }
}
