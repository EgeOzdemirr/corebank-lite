package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidStateTransitionException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotApproveException;
import io.github.egeozdemirr.corebank.transfer.domain.policy.TransferPolicy;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Transfer aggregate. Every status change goes through {@link TransferStatus#canMoveTo}, so an invalid step throws
 * before anything changes, and every step that the contracts publish records an event in the same call.
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

    private Transfer(TransferId id, TransferOrder order, boolean approvalRequired, TransferStatus status,
                     UserId checker, TransferTimeline timeline, FailureReason failureReason) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = Objects.requireNonNull(order, "order");
        this.approvalRequired = approvalRequired;
        this.status = Objects.requireNonNull(status, "status");
        this.checker = checker;
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.failureReason = failureReason;
    }

    /**
     * Records a new transfer: CREATED, then PENDING_APPROVAL above the approval threshold or APPROVED (without a
     * checker) up to it. The single transaction limit is checked first, so an oversized request never becomes a
     * transfer. The daily limit needs the day's other transfers and is checked by the application service.
     */
    public static Transfer request(TransferId id, TransferOrder order, TransferPolicy policy, Instant now) {
        policy.requireWithinSingleTransactionLimit(order.amount());
        boolean approvalRequired = policy.requiresApproval(order.amount());
        Transfer transfer = new Transfer(id, order, approvalRequired, TransferStatus.CREATED, null,
                TransferTimeline.requestedAt(now), null);
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
        return new Transfer(stored.id(), stored.order(), stored.approvalRequired(), stored.status(), stored.checker(),
                stored.timeline(), stored.failureReason());
    }

    /** The state persistence stores; {@link #restore} turns it back into an aggregate. */
    public TransferSnapshot snapshot() {
        return new TransferSnapshot(id, order, approvalRequired, status, checker, timeline, failureReason);
    }

    /** Maker-checker: only a user other than the maker may approve a transfer that waits for approval. */
    public void approve(UserId approver, Instant now) {
        Objects.requireNonNull(approver, "approver");
        requireCanMoveTo(TransferStatus.APPROVED);
        if (approver.equals(order.maker())) {
            throw new MakerCannotApproveException(id);
        }
        moveTo(TransferStatus.APPROVED);
        checker = approver;
        timeline = timeline.approved(now);
        pendingEvents.add(new TransferApproved(id, order, checker(), now));
    }

    public void markPosted(Instant postedAt) {
        moveTo(TransferStatus.POSTED);
        timeline = timeline.posted(postedAt);
        pendingEvents.add(new TransferPosted(id, order, checker(), postedAt));
    }

    /** Only for a definite rejection by account-service; an unknown posting outcome leaves the transfer APPROVED. */
    public void markFailed(FailureReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        moveTo(TransferStatus.FAILED);
        failureReason = reason;
        timeline = timeline.failed(now);
        pendingEvents.add(new TransferFailed(id, order, checker(), reason, now));
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
        requireCanMoveTo(target);
        status = target;
    }

    private void requireCanMoveTo(TransferStatus target) {
        if (!status.canMoveTo(target)) {
            throw new InvalidStateTransitionException(id, status, target);
        }
    }

    public TransferId id() {
        return id;
    }

    public TransferOrder order() {
        return order;
    }

    public boolean approvalRequired() {
        return approvalRequired;
    }

    public TransferStatus status() {
        return status;
    }

    public Optional<UserId> checker() {
        return Optional.ofNullable(checker);
    }

    public TransferTimeline timeline() {
        return timeline;
    }

    public Optional<FailureReason> failureReason() {
        return Optional.ofNullable(failureReason);
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
