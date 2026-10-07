package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.domain.exception.ApprovalExpiredException;
import io.github.egeozdemirr.corebank.transfer.domain.policy.BusinessCalendar;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** A checker approves a transfer; it is posted right after the approval is committed. */
@Service
public class ApproveTransferService {

    private final TransferRecorder recorder;
    private final TransferPostingStep postingStep;
    private final BusinessCalendar businessCalendar;
    private final Clock clock;

    public ApproveTransferService(TransferRecorder recorder, TransferPostingStep postingStep,
                                  BusinessCalendar businessCalendar, Clock clock) {
        this.recorder = recorder;
        this.postingStep = postingStep;
        this.businessCalendar = businessCalendar;
        this.clock = clock;
    }

    /** An overdue transfer is expired and committed first; only then is the expiry reported. */
    public Transfer approve(TransferDecisionCommand command) {
        Instant now = clock.instant();
        TransferRecorder.Decision decision = recorder.decide(command.transferId(),
                transfer -> transfer.approve(command.checker(), businessCalendar, now), now);
        if (decision.expired()) {
            throw new ApprovalExpiredException(command.transferId());
        }
        return postingStep.postIfApproved(decision.transfer());
    }
}
