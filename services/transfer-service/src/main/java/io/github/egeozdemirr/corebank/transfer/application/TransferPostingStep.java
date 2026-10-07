package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.application.port.LedgerPostingGateway;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Posts an APPROVED transfer and records what definitely happened. The call runs outside any transaction. An
 * unknown outcome changes nothing: the transfer stays APPROVED with its limit reserved and is recovered later with the
 * same posting id (roadmap week 3). A posting id conflict is a bug: it is flagged for review and never retried.
 */
@Service
public class TransferPostingStep {

    private static final Logger LOG = LoggerFactory.getLogger(TransferPostingStep.class);

    private final LedgerPostingGateway postingGateway;
    private final TransferRecorder recorder;
    private final Clock clock;

    public TransferPostingStep(LedgerPostingGateway postingGateway, TransferRecorder recorder, Clock clock) {
        this.postingGateway = postingGateway;
        this.recorder = recorder;
        this.clock = clock;
    }

    public Transfer postIfApproved(Transfer transfer) {
        if (transfer.status() != TransferStatus.APPROVED) {
            return transfer;
        }
        PostingOutcome outcome = postingGateway.post(transfer.id(), transfer.order());
        return switch (outcome) {
            case PostingOutcome.Posted posted -> recorder.recordPosting(transfer.id(), posted, clock.instant());
            case PostingOutcome.Rejected rejected -> recorder.recordPosting(transfer.id(), rejected, clock.instant());
            case PostingOutcome.Unknown unknown -> {
                LOG.warn("Transfer {} stays APPROVED: posting outcome unknown ({})", transfer.id(), unknown.cause());
                yield transfer;
            }
            case PostingOutcome.IdConflict conflict -> {
                LOG.error("Transfer {} needs review: its posting id is taken by a different posting in "
                        + "account-service; it stays APPROVED and is not retried", transfer.id());
                recorder.flagForReview(transfer.id(), ReviewReason.POSTING_ID_CONFLICT, clock.instant());
                yield transfer;
            }
        };
    }
}
