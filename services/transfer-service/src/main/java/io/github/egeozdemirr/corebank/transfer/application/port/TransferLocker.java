package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.util.Optional;

/**
 * Loads a transfer that the caller's transaction is about to change and locks it until commit, so two decisions on
 * the same transfer (approve and reject, or two checkers) run one after the other: the second sees the first one's
 * result and fails the state machine instead of overwriting it.
 */
public interface TransferLocker {

    Optional<Transfer> lockById(TransferId transferId);
}
