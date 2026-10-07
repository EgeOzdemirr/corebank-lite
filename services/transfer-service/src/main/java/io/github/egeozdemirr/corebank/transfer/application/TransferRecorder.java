package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.application.port.DailyUsageLedger;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferEventPublisher;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferLocker;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReviewQueue;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferWriter;
import io.github.egeozdemirr.corebank.transfer.domain.exception.DailyLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.TransferNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.policy.BusinessCalendar;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database transactions of the transfer lifecycle. The remote posting call always happens between two of
 * them, never inside one, so no row lock or connection is held while account-service answers (ADR-0005). Each method
 * writes the business change, the daily limit change and the outbox events in one transaction.
 */
@Service
public class TransferRecorder {

    private final TransferWriter transferWriter;
    private final TransferLocker transferLocker;
    private final DailyUsageLedger dailyUsageLedger;
    private final TransferEventPublisher eventPublisher;
    private final TransferReviewQueue reviewQueue;
    private final BusinessCalendar businessCalendar;

    public TransferRecorder(TransferWriter transferWriter, TransferLocker transferLocker,
                            DailyUsageLedger dailyUsageLedger, TransferEventPublisher eventPublisher,
                            TransferReviewQueue reviewQueue, BusinessCalendar businessCalendar) {
        this.transferWriter = transferWriter;
        this.transferLocker = transferLocker;
        this.dailyUsageLedger = dailyUsageLedger;
        this.eventPublisher = eventPublisher;
        this.reviewQueue = reviewQueue;
        this.businessCalendar = businessCalendar;
    }

    /** Reserves the daily limit and records the new transfer; over the limit nothing is recorded at all. */
    @Transactional
    public Transfer recordRequest(Transfer transfer, Money dailyLimit) {
        LocalDate businessDay = businessCalendar.businessDayOf(transfer.snapshot().timeline().requestedAt());
        Money amount = transfer.order().amount();
        if (!dailyUsageLedger.reserve(transfer.order().source().accountId(), businessDay, amount, dailyLimit)) {
            throw new DailyLimitExceededException(amount, dailyLimit);
        }
        transferWriter.add(transfer, businessDay);
        transfer.pullEvents().forEach(eventPublisher::publish);
        return transfer;
    }

    /**
     * Applies a checker's decision under a row lock, so two decisions on one transfer run one after the other. An
     * overdue approval is expired instead and committed as such; the caller reports the expiry only afterwards, so
     * the 409 never rolls the expiry back.
     */
    @Transactional
    public Decision decide(TransferId transferId, Consumer<Transfer> decision, Instant now) {
        Transfer transfer = transferLocker.lockById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        TransferStatus before = transfer.status();
        boolean expired = transfer.isApprovalOverdue(businessCalendar, now);
        if (expired) {
            transfer.expireApproval(businessCalendar, now);
        } else {
            decision.accept(transfer);
        }
        save(transfer, before);
        return new Decision(transfer, expired);
    }

    /** Records a definite posting result: POSTED, or FAILED with the daily limit given back. */
    @Transactional
    public Transfer recordPosting(TransferId transferId, PostingOutcome outcome, Instant now) {
        Transfer transfer = transferLocker.lockById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        TransferStatus before = transfer.status();
        switch (outcome) {
            case PostingOutcome.Posted posted -> transfer.markPosted(posted.postedAt());
            case PostingOutcome.Rejected rejected -> transfer.markFailed(rejected.reason(), now);
            case PostingOutcome.Unknown unknown ->
                    throw new IllegalArgumentException("An unknown posting outcome changes nothing: " + unknown);
            case PostingOutcome.IdConflict conflict ->
                    throw new IllegalArgumentException("A posting id conflict is flagged, not recorded");
        }
        save(transfer, before);
        return transfer;
    }

    @Transactional
    public void flagForReview(TransferId transferId, ReviewReason reason, Instant now) {
        reviewQueue.flag(transferId, reason, now);
    }

    /** The move to FAILED, and only that move, gives the reserved amount back; REVERSED keeps it. */
    private void save(Transfer transfer, TransferStatus before) {
        if (before != TransferStatus.FAILED && transfer.status() == TransferStatus.FAILED) {
            dailyUsageLedger.release(transfer.id());
        }
        transferWriter.update(transfer);
        transfer.pullEvents().forEach(eventPublisher::publish);
    }

    /** {@code expired}: the approval window was over, so the transfer was expired instead of decided. */
    public record Decision(Transfer transfer, boolean expired) {
    }
}
