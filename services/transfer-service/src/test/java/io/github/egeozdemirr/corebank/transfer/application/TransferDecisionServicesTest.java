package io.github.egeozdemirr.corebank.transfer.application;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.CHECKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.MAKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NEXT_BUSINESS_DAY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.ApprovalExpiredException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidStateTransitionException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotApproveException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotRejectException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.TransferNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferApproved;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferFailed;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferPosted;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import org.junit.jupiter.api.Test;

/** Approval and rejection by a checker, and the expiry that replaces both after the business day. */
class TransferDecisionServicesTest {

    private final TransferServicesFixture fixture = new TransferServicesFixture();

    @Test
    void approvalByAChecker_isPostedRightAway() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));

        Transfer approved = fixture.approveService.approve(new TransferDecisionCommand(pending.id(), CHECKER));

        assertThat(approved.status()).isEqualTo(TransferStatus.POSTED);
        assertThat(approved.snapshot().checker()).isEqualTo(CHECKER);
        assertThat(fixture.ledger.calls()).containsExactly(pending.id());
        assertThat(fixture.store.publishedEvents().subList(1, 3))
                .hasExactlyElementsOfTypes(TransferApproved.class, TransferPosted.class);
    }

    @Test
    void approvalWithUnknownPostingOutcome_staysApproved() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));
        fixture.ledger.willAnswer(new PostingOutcome.Unknown("UNAVAILABLE"));

        Transfer approved = fixture.approveService.approve(new TransferDecisionCommand(pending.id(), CHECKER));

        assertThat(approved.status()).isEqualTo(TransferStatus.APPROVED);
        assertThat(fixture.usedToday()).isEqualTo(money("60000.00"));
    }

    @Test
    void makerCannotDecideOnTheirOwnTransfer() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));

        assertThatThrownBy(() -> fixture.approveService.approve(new TransferDecisionCommand(pending.id(), MAKER)))
                .isInstanceOf(MakerCannotApproveException.class);
        assertThatThrownBy(() -> fixture.rejectService.reject(new TransferDecisionCommand(pending.id(), MAKER)))
                .isInstanceOf(MakerCannotRejectException.class);
        assertThat(fixture.store.stored(pending.id()).status()).isEqualTo(TransferStatus.PENDING_APPROVAL);
        assertThat(fixture.ledger.calls()).isEmpty();
    }

    @Test
    void rejectionByAChecker_failsTheTransferAndGivesTheLimitBack() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));

        Transfer rejected = fixture.rejectService.reject(new TransferDecisionCommand(pending.id(), CHECKER));

        assertThat(rejected.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(rejected.snapshot().failureReason()).isEqualTo(FailureReason.REJECTED_BY_CHECKER);
        assertThat(fixture.store.stored(pending.id()).snapshot().checker()).isEqualTo(CHECKER);
        assertThat(fixture.usedToday()).isEqualTo(money("0.00"));
        assertThat(fixture.ledger.calls()).isEmpty();
    }

    @Test
    void secondDecision_findsTheFirstOneAndFails() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));
        fixture.rejectService.reject(new TransferDecisionCommand(pending.id(), CHECKER));

        assertThatThrownBy(() -> fixture.approveService.approve(new TransferDecisionCommand(pending.id(), CHECKER)))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(fixture.usedToday()).isEqualTo(money("0.00"));
    }

    /** The expiry is stored (FAILED, limit back) before the 409 is reported, and the 409 does not undo it. */
    @Test
    void decisionAfterTheBusinessDay_expiresTheTransferAndThenReportsIt() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));
        fixture.clock.setInstant(NEXT_BUSINESS_DAY);

        assertThatThrownBy(() -> fixture.approveService.approve(new TransferDecisionCommand(pending.id(), CHECKER)))
                .isInstanceOf(ApprovalExpiredException.class);

        Transfer stored = fixture.store.stored(pending.id());
        assertThat(stored.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(stored.snapshot().failureReason()).isEqualTo(FailureReason.APPROVAL_EXPIRED);
        assertThat(stored.snapshot().checker()).isNull();
        assertThat(fixture.usedToday()).isEqualTo(money("0.00"));
        assertThat(fixture.store.publishedEvents().getLast()).isInstanceOfSatisfying(TransferFailed.class,
                event -> assertThat(event.reason()).isEqualTo(FailureReason.APPROVAL_EXPIRED));
        assertThat(fixture.ledger.calls()).isEmpty();
    }

    @Test
    void rejectionAfterTheBusinessDay_expiresTheTransferToo() {
        Transfer pending = fixture.createService.create(fixture.command("60000.00"));
        fixture.clock.setInstant(NEXT_BUSINESS_DAY);

        assertThatThrownBy(() -> fixture.rejectService.reject(new TransferDecisionCommand(pending.id(), CHECKER)))
                .isInstanceOf(ApprovalExpiredException.class);
        assertThat(fixture.store.stored(pending.id()).snapshot().failureReason())
                .isEqualTo(FailureReason.APPROVAL_EXPIRED);
    }

    @Test
    void unknownTransfer_isNotFoundForEveryUseCase() {
        TransferId unknown = TransferId.newId();

        assertThatThrownBy(() -> fixture.approveService.approve(new TransferDecisionCommand(unknown, CHECKER)))
                .isInstanceOf(TransferNotFoundException.class);
        assertThatThrownBy(() -> fixture.rejectService.reject(new TransferDecisionCommand(unknown, CHECKER)))
                .isInstanceOf(TransferNotFoundException.class);
        assertThatThrownBy(() -> fixture.queryService.getTransfer(unknown))
                .isInstanceOf(TransferNotFoundException.class);
    }

    @Test
    void query_returnsTheStoredTransfer() {
        Transfer posted = fixture.createService.create(fixture.command("10.00"));

        assertThat(fixture.queryService.getTransfer(posted.id()).snapshot()).isEqualTo(posted.snapshot());
    }

    @Test
    void recorder_refusesToRecordOutcomesThatChangeNothing() {
        TransferRecorder recorder = new TransferRecorder(fixture.store, fixture.store, fixture.store, fixture.store,
                fixture.store, ISTANBUL);
        Transfer posted = fixture.createService.create(fixture.command("10.00"));

        assertThatThrownBy(() -> recorder.recordPosting(posted.id(), new PostingOutcome.Unknown("x"),
                NEXT_BUSINESS_DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.recordPosting(posted.id(), new PostingOutcome.IdConflict(),
                NEXT_BUSINESS_DAY)).isInstanceOf(IllegalArgumentException.class);
    }
}
