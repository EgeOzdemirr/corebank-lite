package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.CHECKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.MAKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TARGET_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.USD;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.account;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidStateTransitionException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotApproveException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.SingleTransactionLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.UnsupportedCurrencyException;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferTest {

    private static final Instant LATER = NOW.plusSeconds(60);
    private static final FailureReason INSUFFICIENT_FUNDS = new FailureReason("INSUFFICIENT_FUNDS");

    @Test
    void requestUpToTheThreshold_isApprovedAutomaticallyWithoutChecker() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("50000.00"), POLICY, NOW);

        TransferSnapshot state = transfer.snapshot();
        assertThat(state.status()).isEqualTo(TransferStatus.APPROVED);
        assertThat(state.approvalRequired()).isFalse();
        assertThat(state.checker()).isNull();
        assertThat(state.timeline().approvedAt()).contains(NOW);
        assertThat(transfer.pullEvents()).satisfiesExactly(
                requested -> assertThat(requested).isInstanceOfSatisfying(TransferRequested.class, event -> {
                    assertThat(event.approvalRequired()).isFalse();
                    assertThat(event.checker()).isEmpty();
                    assertThat(event.occurredAt()).isEqualTo(NOW);
                }),
                approved -> assertThat(approved).isInstanceOfSatisfying(TransferApproved.class, event -> {
                    assertThat(event.checker()).isEmpty();
                    assertThat(event.occurredAt()).isEqualTo(NOW);
                }));
    }

    @Test
    void requestAboveTheThreshold_waitsForApproval() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("50000.01"), POLICY, NOW);

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING_APPROVAL);
        assertThat(transfer.snapshot().approvalRequired()).isTrue();
        assertThat(transfer.snapshot().timeline().approvedAt()).isEmpty();
        assertThat(transfer.pullEvents()).singleElement().isInstanceOfSatisfying(TransferRequested.class,
                event -> assertThat(event.approvalRequired()).isTrue());
    }

    @Test
    void requestAboveTheSingleTransactionLimit_neverBecomesATransfer() {
        assertThatThrownBy(() -> Transfer.request(TransferId.newId(), order("100000.01"), POLICY, NOW))
                .isInstanceOf(SingleTransactionLimitExceededException.class);
    }

    @Test
    void requestInACurrencyWithoutPolicy_isUnsupported() {
        TransferOrder dollars = new TransferOrder(account(SOURCE_IBAN, USD), account(TARGET_IBAN, USD),
                new BeneficiaryName("Mehmet Demir"), Money.of("10.00", USD), TransferChannel.MOBILE, MAKER);

        assertThatThrownBy(() -> Transfer.request(TransferId.newId(), dollars, POLICY, NOW))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void approvalByAnotherUser_recordsTheChecker() {
        Transfer transfer = pendingApproval();

        transfer.approve(CHECKER, ISTANBUL, LATER);

        assertThat(transfer.status()).isEqualTo(TransferStatus.APPROVED);
        assertThat(transfer.snapshot().checker()).isEqualTo(CHECKER);
        assertThat(transfer.snapshot().timeline().approvedAt()).contains(LATER);
        assertThat(transfer.pullEvents()).singleElement().isInstanceOfSatisfying(TransferApproved.class, event -> {
            assertThat(event.checker()).contains(CHECKER);
            assertThat(event.occurredAt()).isEqualTo(LATER);
        });
    }

    @Test
    void approvalByTheMaker_isRejectedAndChangesNothing() {
        Transfer transfer = pendingApproval();

        assertThatThrownBy(() -> transfer.approve(MAKER, ISTANBUL, LATER))
                .isInstanceOf(MakerCannotApproveException.class);
        assertThat(transfer.snapshot()).isEqualTo(pendingSnapshotOf(transfer));
        assertThat(transfer.pullEvents()).isEmpty();
    }

    @Test
    void makerCheck_comparesUsersNotInstances() {
        Transfer transfer = pendingApproval();

        assertThatThrownBy(() -> transfer.approve(new UserId(MAKER.value()), ISTANBUL, LATER))
                .isInstanceOf(MakerCannotApproveException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"APPROVED", "POSTED", "FAILED", "REVERSED"})
    void approval_isOnlyPossibleWhilePending(TransferStatus status) {
        Transfer transfer = stored(status);

        assertThatThrownBy(() -> transfer.approve(CHECKER, ISTANBUL, LATER))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(status + " to APPROVED");
        assertThat(transfer.status()).isEqualTo(status);
    }

    @Test
    void wrongStatus_isReportedBeforeTheMakerCheck() {
        Transfer posted = stored(TransferStatus.POSTED);

        assertThatThrownBy(() -> posted.approve(MAKER, ISTANBUL, LATER))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void posting_movesAnApprovedTransferToPosted() {
        Transfer transfer = stored(TransferStatus.APPROVED, CHECKER);

        transfer.markPosted(LATER);

        assertThat(transfer.status()).isEqualTo(TransferStatus.POSTED);
        assertThat(transfer.snapshot().timeline().postedAt()).contains(LATER);
        assertThat(transfer.pullEvents()).singleElement().isInstanceOfSatisfying(TransferPosted.class, event -> {
            assertThat(event.checker()).contains(CHECKER);
            assertThat(event.occurredAt()).isEqualTo(LATER);
        });
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"PENDING_APPROVAL", "POSTED", "FAILED", "REVERSED"})
    void posting_requiresAnApprovedTransfer(TransferStatus status) {
        Transfer transfer = stored(status);

        assertThatThrownBy(() -> transfer.markPosted(LATER)).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(transfer.status()).isEqualTo(status);
        assertThat(transfer.snapshot().timeline().postedAt()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"PENDING_APPROVAL", "APPROVED"})
    void definiteRejection_failsAPendingOrApprovedTransfer(TransferStatus status) {
        Transfer transfer = stored(status);

        transfer.markFailed(INSUFFICIENT_FUNDS, LATER);

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.snapshot().failureReason()).isEqualTo(INSUFFICIENT_FUNDS);
        assertThat(transfer.snapshot().timeline().failedAt()).contains(LATER);
        assertThat(transfer.pullEvents()).singleElement().isInstanceOfSatisfying(TransferFailed.class, event -> {
            assertThat(event.reason()).isEqualTo(INSUFFICIENT_FUNDS);
            assertThat(event.occurredAt()).isEqualTo(LATER);
        });
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"POSTED", "FAILED", "REVERSED"})
    void failing_isNotPossibleOncePostedOrFinal(TransferStatus status) {
        Transfer transfer = stored(status);

        assertThatThrownBy(() -> transfer.markFailed(INSUFFICIENT_FUNDS, LATER))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(transfer.snapshot().failureReason()).isNull();
    }

    @Test
    void reversal_undoesAPostedTransferWithoutPublishingYet() {
        Transfer transfer = stored(TransferStatus.POSTED);

        transfer.reverse(LATER);

        assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSED);
        assertThat(transfer.snapshot().timeline().reversedAt()).contains(LATER);
        assertThat(transfer.pullEvents()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"PENDING_APPROVAL", "APPROVED", "FAILED", "REVERSED"})
    void reversal_requiresAPostedTransfer(TransferStatus status) {
        Transfer transfer = stored(status);

        assertThatThrownBy(() -> transfer.reverse(LATER)).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(transfer.status()).isEqualTo(status);
    }

    @Test
    void createdTransfer_isNeverRestored() {
        TransferSnapshot created = new TransferSnapshot(TransferId.newId(), order("1.00"), false,
                TransferStatus.CREATED, null, TransferTimeline.requestedAt(NOW), null);

        assertThatThrownBy(() -> Transfer.restore(created)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshot_roundTripsThroughRestore() {
        Transfer original = Transfer.request(TransferId.newId(), order("75000.00"), POLICY, NOW);
        original.approve(CHECKER, ISTANBUL, LATER);

        Transfer restored = Transfer.restore(original.snapshot());

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        assertThat(restored).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(restored.order()).isEqualTo(original.order());
        assertThat(restored.pullEvents()).isEmpty();
    }

    @Test
    void pullEvents_handsOutEachEventOnce() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("10.00"), POLICY, NOW);

        assertThat(transfer.pullEvents()).hasSize(2);
        assertThat(transfer.pullEvents()).isEmpty();
    }

    @Test
    void toString_carriesNoPersonalData() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("10.00"), POLICY, NOW);

        assertThat(transfer.toString())
                .contains(transfer.id().toString(), "APPROVED", "10.00 TRY")
                .doesNotContain(SOURCE_IBAN, TARGET_IBAN, "Mehmet Demir");
        assertThat(transfer).isNotEqualTo(Transfer.request(TransferId.newId(), order("10.00"), POLICY, NOW));
    }

    private static Transfer pendingApproval() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("60000.00"), POLICY, NOW);
        transfer.pullEvents();
        return transfer;
    }

    private static TransferSnapshot pendingSnapshotOf(Transfer transfer) {
        return new TransferSnapshot(transfer.id(), transfer.order(), true, TransferStatus.PENDING_APPROVAL, null,
                TransferTimeline.requestedAt(NOW), null);
    }

    static Transfer stored(TransferStatus status) {
        return stored(status, null);
    }

    static Transfer stored(TransferStatus status, UserId checker) {
        return Transfer.restore(new TransferSnapshot(TransferId.newId(), order("60000.00"), true, status, checker,
                TransferTimeline.requestedAt(NOW), null));
    }
}
