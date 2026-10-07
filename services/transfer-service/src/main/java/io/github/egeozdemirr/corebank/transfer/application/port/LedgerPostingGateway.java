package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.application.PostingOutcome;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;

/**
 * Posts a transfer's money movement in account-service under the transfer id as posting id, which makes a repeated
 * call safe. Never throws for remote failures: every result, including "unknown", is a {@link PostingOutcome}.
 */
public interface LedgerPostingGateway {

    PostingOutcome post(TransferId transferId, TransferOrder order);
}
