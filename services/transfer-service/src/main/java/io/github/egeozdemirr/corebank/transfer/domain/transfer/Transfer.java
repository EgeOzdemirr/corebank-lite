package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.exception.ApprovalExpiredException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidStateTransitionException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotApproveException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotRejectException;
import io.github.egeozdemirr.corebank.transfer.domain.policy.BusinessCalendar;
import io.github.egeozdemirr.corebank.transfer.domain.policy.TransferPolicy;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Transfer aggregate. Every status change goes through {@link TransferStatus#canMoveTo}, so an invalid step throws
 * before anything changes, and every step that the contracts publish records an event in the same call. The full
 * state is read through {@link #snapshot()}; the aggregate itself offers behaviour.
 */
public final class Transfer {

    private final TransferId id;
    private final TransferOrder order;
    private final boolean approvalRequired;
    private final List<TransferEvent> pendingEvents = new ArrayList<>();
    private TransferStatus status;
    private UserId checker;
    private TransferTimeline timeline;
    private FailureReason failureReason;

    private Transfer(TransferSnapshot state) {
        this.id = state.id();
        this.order = state.order();
        this.approvalRequired = state.approvalRequired();
        this.status = state.status();
        this.checker = state.checker();
        this.timeline = state.timeline();
        this.failureReason = state.failureReason();
    }

    /**
     * Records a new transfer: CREATED, then PENDING_APPROVAL above the approval threshold or APPROVED (without a
     * checker) up to it. The single transaction limit is checked first, so an oversized request never becomes a
     * transfer. The daily limit needs the day's other transfers and is checked by the application service.
     */
    public static Transfer request(TransferId id, TransferOrder order, TransferPolicy policy, Instant now) {
        policy.requireWithinSingleTransactionLimit(order.amount());
        boolean approvalRequired = policy.requiresApproval(order.amount());
        Transfer transfer = new Transfer(new TransferSnapshot(id, order, approvalRequired, TransferStatus.CREATED,
                null, TransferTimeline.requestedAt(now), null));
        transfer.pendingEvents.add(new TransferRequested(id, order, approvalRequired, now));
        if (approvalRequired) {
            transfer.moveTo(TransferStatus.PENDING_APPROVAL);
        } else {
            transfer.moveTo(TransferStatus.APPROVED);
            transfer.timeline = transfer.timeline.approved(now);
            transfer.pendingEvents.add(new TransferApproved(id, order, Optional.empty(), now));
        }
        return transfer;
    }

    /** Rebuilds a stored transfer; no business rule is applied. CREATED is never stored, it lasts one call. */
    public static Transfer restore(TransferSnapshot stored) {
        if (stored.status() == TransferStatus.CREATED) {
            throw new IllegalArgumentException("Transfer " + stored.id() + " cannot be restored in status CREATED");
        }
        return new Transfer(stored);
    }

    /** The state persistence stores and readers display; {@link #restore} turns it back into an aggregate. */
    public TransferSnapshot snapshot() {
        return new TransferSnapshot(id, order, approvalRequired, status, checker, timeline, failureReason);
    }

    /**
     * Maker-checker: only a user other than the maker may approve, and only within the business day of the request.
     * Checks run in a fixed order (status, deadline, maker) so the reported reason does not depend on who asks.
     */
    public void approve(UserId approver, BusinessCalendar calendar, Instant now) {
        Objects.requireNonNull(approver, "approver");
        requirePendingApproval(TransferStatus.APPROVED);
        requireApprovalNotOverdue(calendar, now);
        if (approver.equals(order.maker())) {
            throw new MakerCannotApproveException(id);
        }
        moveTo(TransferStatus.APPROVED);
        checker = approver;
        timeline = timeline.approved(now);
        pendingEvents.add(new TransferApproved(id, order, Optional.of(approver), now));
    }

    /** The checker's "no": FAILED with REJECTED_BY_CHECKER, and the rejecting user is recorded as the checker. */
    public void reject(UserId rejector, BusinessCalendar calendar, Instant now) {
        Objects.requireNonNull(rejector, "rejector");
        requirePendingApproval(TransferStatus.FAILED);
        requireApprovalNotOverdue(calendar, now);
        if (rejector.equals(order.maker())) {
            throw new MakerCannotRejectException(id);
        }
        checker = rejector;
        markFailed(FailureReason.REJECTED_BY_CHECKER, now);
    }

    /**
     * True while the transfer waits for a checker although the business day of its request is over. Approval is
     * meant to happen the same day; a transfer that waited longer has to be requested again by its maker.
     */
    public boolean isApprovalOverdue(BusinessCalendar calendar, Instant now) {
        return status == TransferStatus.PENDING_APPROVAL
                && calendar.businessDayOf(now).isAfter(calendar.businessDayOf(timeline.requestedAt()));
    }

    /** Ends an overdue approval: FAILED with APPROVAL_EXPIRED. Nobody decided, so there is no checker. */
    public void expireApproval(BusinessCalendar calendar, Instant now) {
        requirePendingApproval(TransferStatus.FAILED);
        if (!isApprovalOverdue(calendar, now)) {
            throw new IllegalStateException("Transfer " + id + " is still within its approval day");
        }
        markFailed(FailureReason.APPROVAL_EXPIRED, now);
    }

    public void markPosted(Instant postedAt) {
        moveTo(TransferStatus.POSTED);
        timeline = timeline.posted(postedAt);
        pendingEvents.add(new TransferPosted(id, order, Optional.ofNullable(checker), postedAt));
    }

    /**
     * FAILED with a stable reason: a definite rejection by account-service, a checker's rejection or an expired
     * approval. An unknown posting outcome must not end here; it leaves the transfer APPROVED.
     */
    public void markFailed(FailureReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        moveTo(TransferStatus.FAILED);
        failureReason = reason;
        timeline = timeline.failed(now);
        pendingEvents.add(new TransferFailed(id, order, Optional.ofNullable(checker), reason, now));
    }

    /** The step itself; the compensating posting and its event arrive with the saga (roadmap week 3). */
    public void reverse(Instant now) {
        moveTo(TransferStatus.REVERSED);
        timeline = timeline.reversed(now);
    }

    /** Events recorded since the last call, oldest first; the caller writes them to the outbox. */
    public List<TransferEvent> pullEvents() {
        List<TransferEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    private void moveTo(TransferStatus target) {
        if (!status.canMoveTo(target)) {
            throw new InvalidStateTransitionException(id, status, target);
        }
        status = target;
    }

    /** Approval decisions start from PENDING_APPROVAL only; FAILED is also reachable from APPROVED, so be explicit. */
    private void requirePendingApproval(TransferStatus target) {
        if (status != TransferStatus.PENDING_APPROVAL) {
            throw new InvalidStateTransitionException(id, status, target);
        }
    }

    private void requireApprovalNotOverdue(BusinessCalendar calendar, Instant now) {
        if (isApprovalOverdue(calendar, now)) {
            throw new ApprovalExpiredException(id);
        }
    }

    public TransferId id() {
        return id;
    }

    public TransferOrder order() {
        return order;
    }

    public TransferStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Transfer transfer && id.equals(transfer.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Transfer[id=" + id + ", status=" + status + ", amount=" + order.amount() + "]";
    }
}
