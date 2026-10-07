package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.CHECKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.MAKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NEXT_BUSINESS_DAY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.ApprovalExpiredException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidStateTransitionException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotApproveException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotRejectException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Checker rejection and approval expiry: the two ways a transfer waiting for approval ends without being posted. */
class ApprovalDecisionTest {

    /** The last instant of the request's Istanbul business day (NOW is 12:30 Istanbul time on 2026-10-07). */
    private static final Instant END_OF_REQUEST_DAY = Instant.parse("2026-10-07T20:59:59.999999Z");

    @Test
    void rejectionByAChecker_failsTheTransferAndRecordsTheChecker() {
        Transfer transfer = pendingApproval();

        transfer.reject(CHECKER, ISTANBUL, END_OF_REQUEST_DAY);

        TransferSnapshot state = transfer.snapshot();
        assertThat(state.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(state.failureReason()).isEqualTo(FailureReason.REJECTED_BY_CHECKER);
        assertThat(state.checker()).isEqualTo(CHECKER);
        assertThat(state.timeline().failedAt()).contains(END_OF_REQUEST_DAY);
        assertThat(transfer.pullEvents()).singleElement().isInstanceOfSatisfying(TransferFailed.class, event -> {
            assertThat(event.reason()).isEqualTo(FailureReason.REJECTED_BY_CHECKER);
            assertThat(event.checker()).contains(CHECKER);
        });
    }

    @Test
    void rejectionByTheMaker_isNotAllowed() {
        Transfer transfer = pendingApproval();

        assertThatThrownBy(() -> transfer.reject(MAKER, ISTANBUL, NOW))
                .isInstanceOf(MakerCannotRejectException.class);
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING_APPROVAL);
        assertThat(transfer.snapshot().checker()).isNull();
        assertThat(transfer.pullEvents()).isEmpty();
    }

    /** FAILED is also reachable from APPROVED, but a checker can only reject what still waits for a decision. */
    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"APPROVED", "POSTED", "FAILED", "REVERSED"})
    void rejection_isOnlyPossibleWhilePending(TransferStatus status) {
        Transfer transfer = TransferTest.stored(status);

        assertThatThrownBy(() -> transfer.reject(CHECKER, ISTANBUL, NOW))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(status + " to FAILED");
        assertThat(transfer.status()).isEqualTo(status);
    }

    @Test
    void approval_isOverdueFromTheFirstInstantOfTheNextIstanbulDay() {
        Transfer transfer = pendingApproval();

        assertThat(transfer.isApprovalOverdue(ISTANBUL, END_OF_REQUEST_DAY)).isFalse();
        assertThat(transfer.isApprovalOverdue(ISTANBUL, NEXT_BUSINESS_DAY)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"APPROVED", "POSTED", "FAILED", "REVERSED"})
    void onlyAWaitingTransfer_canBeOverdue(TransferStatus status) {
        assertThat(TransferTest.stored(status).isApprovalOverdue(ISTANBUL, NEXT_BUSINESS_DAY)).isFalse();
    }

    @Test
    void approvalWithinTheRequestDay_isStillPossible() {
        Transfer transfer = pendingApproval();

        transfer.approve(CHECKER, ISTANBUL, END_OF_REQUEST_DAY);

        assertThat(transfer.status()).isEqualTo(TransferStatus.APPROVED);
    }

    @Test
    void overdueApprovalOrRejection_isRefusedAndChangesNothing() {
        Transfer transfer = pendingApproval();

        assertThatThrownBy(() -> transfer.approve(CHECKER, ISTANBUL, NEXT_BUSINESS_DAY))
                .isInstanceOf(ApprovalExpiredException.class);
        assertThatThrownBy(() -> transfer.reject(CHECKER, ISTANBUL, NEXT_BUSINESS_DAY))
                .isInstanceOf(ApprovalExpiredException.class);
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING_APPROVAL);
        assertThat(transfer.pullEvents()).isEmpty();
    }

    /** The deadline is checked before the maker, so the maker learns that the transfer expired, not who may act. */
    @Test
    void expiry_isReportedBeforeTheMakerCheck() {
        Transfer transfer = pendingApproval();

        assertThatThrownBy(() -> transfer.approve(MAKER, ISTANBUL, NEXT_BUSINESS_DAY))
                .isInstanceOf(ApprovalExpiredException.class)
                .isNotInstanceOf(MakerCannotApproveException.class);
    }

    @Test
    void expiry_failsTheTransferWithoutAChecker() {
        Transfer transfer = pendingApproval();

        transfer.expireApproval(ISTANBUL, NEXT_BUSINESS_DAY);

        TransferSnapshot state = transfer.snapshot();
        assertThat(state.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(state.failureReason()).isEqualTo(FailureReason.APPROVAL_EXPIRED);
        assertThat(state.checker()).isNull();
        assertThat(transfer.pullEvents()).singleElement().isInstanceOfSatisfying(TransferFailed.class, event -> {
            assertThat(event.reason()).isEqualTo(FailureReason.APPROVAL_EXPIRED);
            assertThat(event.checker()).isEmpty();
            assertThat(event.occurredAt()).isEqualTo(NEXT_BUSINESS_DAY);
        });
    }

    @Test
    void expiryWithinTheRequestDay_isAProgrammingError() {
        Transfer transfer = pendingApproval();

        assertThatThrownBy(() -> transfer.expireApproval(ISTANBUL, END_OF_REQUEST_DAY))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING_APPROVAL);
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"APPROVED", "POSTED", "FAILED", "REVERSED"})
    void expiry_appliesOnlyToWaitingTransfers(TransferStatus status) {
        Transfer transfer = TransferTest.stored(status);

        assertThatThrownBy(() -> transfer.expireApproval(ISTANBUL, NEXT_BUSINESS_DAY))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void failureReasonConstants_areValidCodes() {
        assertThat(FailureReason.REJECTED_BY_CHECKER.code()).isEqualTo("REJECTED_BY_CHECKER");
        assertThat(FailureReason.APPROVAL_EXPIRED.code()).isEqualTo("APPROVAL_EXPIRED");
    }

    private static Transfer pendingApproval() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("60000.00"), POLICY, NOW);
        transfer.pullEvents();
        return transfer;
    }
}
