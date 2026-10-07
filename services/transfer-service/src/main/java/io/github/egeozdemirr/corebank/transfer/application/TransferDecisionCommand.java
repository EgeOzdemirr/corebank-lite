package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.util.Objects;

/** A checker approves or rejects a transfer that waits for approval. */
public record TransferDecisionCommand(TransferId transferId, UserId checker) {

    public TransferDecisionCommand {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(checker, "checker");
    }
}
