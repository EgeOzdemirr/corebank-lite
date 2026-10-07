package io.github.egeozdemirr.corebank.transfer.application;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.MAKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TRY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.USD;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotActiveException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.DailyLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.SingleTransactionLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.UnsupportedCurrencyException;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.BeneficiaryName;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferApproved;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferChannel;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferFailed;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferPosted;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferRequested;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import io.github.egeozdemirr.corebank.transfer.support.ScriptedPostingGateway;
import io.github.egeozdemirr.corebank.transfer.support.TestTransfers;
import org.junit.jupiter.api.Test;

class CreateTransferServiceTest {

    private final TransferServicesFixture fixture = new TransferServicesFixture();

    @Test
    void transferUpToTheThreshold_isApprovedAndPostedAtOnce() {
        Transfer transfer = fixture.createService.create(fixture.command("50000.00"));

        assertThat(transfer.status()).isEqualTo(TransferStatus.POSTED);
        assertThat(transfer.snapshot().timeline().postedAt()).contains(ScriptedPostingGateway.POSTED_AT);
        assertThat(fixture.store.stored(transfer.id()).status()).isEqualTo(TransferStatus.POSTED);
        assertThat(fixture.store.publishedEvents()).hasExactlyElementsOfTypes(TransferRequested.class,
                TransferApproved.class, TransferPosted.class);
        assertThat(fixture.usedToday()).isEqualTo(money("50000.00"));
    }

    @Test
    void transferAboveTheThreshold_waitsForApprovalAndIsNotPosted() {
        Transfer transfer = fixture.createService.create(fixture.command("50000.01"));

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING_APPROVAL);
        assertThat(fixture.ledger.calls()).isEmpty();
        assertThat(fixture.usedToday()).isEqualTo(money("50000.01"));
    }

    @Test
    void definiteRejection_failsTheTransferAndGivesTheLimitBack() {
        fixture.ledger.willAnswer(new PostingOutcome.Rejected(new FailureReason("INSUFFICIENT_FUNDS")));

        Transfer transfer = fixture.createService.create(fixture.command("100.00"));

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.snapshot().failureReason()).isEqualTo(new FailureReason("INSUFFICIENT_FUNDS"));
        assertThat(fixture.store.publishedEvents().getLast()).isInstanceOf(TransferFailed.class);
        assertThat(fixture.usedToday()).isEqualTo(money("0.00"));
    }

    @Test
    void unknownOutcome_leavesTheTransferApprovedWithItsLimitReserved() {
        fixture.ledger.willAnswer(new PostingOutcome.Unknown("DEADLINE_EXCEEDED"));

        Transfer transfer = fixture.createService.create(fixture.command("100.00"));

        assertThat(transfer.status()).isEqualTo(TransferStatus.APPROVED);
        assertThat(fixture.store.stored(transfer.id()).status()).isEqualTo(TransferStatus.APPROVED);
        assertThat(fixture.usedToday()).isEqualTo(money("100.00"));
        assertThat(fixture.store.publishedEvents()).noneMatch(TransferFailed.class::isInstance);
        assertThat(fixture.store.isFlaggedForReview(transfer.id())).isFalse();
    }

    @Test
    void postingIdConflict_staysApprovedAndIsFlaggedForReview() {
        fixture.ledger.willAnswer(new PostingOutcome.IdConflict());

        Transfer transfer = fixture.createService.create(fixture.command("100.00"));

        assertThat(transfer.status()).isEqualTo(TransferStatus.APPROVED);
        assertThat(fixture.store.isFlaggedForReview(transfer.id())).isTrue();
        assertThat(fixture.ledger.calls()).hasSize(1);
    }

    @Test
    void dailyLimit_isCheckedBeforeAnythingIsRecorded() {
        fixture.createService.create(fixture.command("100000.00"));
        fixture.createService.create(fixture.command("100000.00"));

        assertThatThrownBy(() -> fixture.createService.create(fixture.command("50000.01")))
                .isInstanceOf(DailyLimitExceededException.class);
        assertThat(fixture.store.storedCount()).isEqualTo(2);
        assertThat(fixture.usedToday()).isEqualTo(money("200000.00"));
        assertThat(fixture.createService.create(fixture.command("50000.00")).status())
                .isEqualTo(TransferStatus.POSTED);
    }

    /** Local checks come first, so an invalid request never waits for account-service. */
    @Test
    void localRejections_neverReachAccountService() {
        assertThatThrownBy(() -> fixture.createService.create(fixture.command("100000.01")))
                .isInstanceOf(SingleTransactionLimitExceededException.class);
        assertThatThrownBy(() -> fixture.createService.create(fixture.command(Money.of("1.00", USD), MAKER)))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThat(fixture.accounts.lookups()).isZero();
        assertThat(fixture.store.storedCount()).isZero();
    }

    @Test
    void closedOrUnknownAccounts_areRejectedBeforeAnythingIsRecorded() {
        TransferServicesFixture closedTarget = new TransferServicesFixture();
        closedTarget.accounts.add(TestTransfers.account("TR269999900000000000000003", TRY), false);
        CreateTransferCommand toClosed = new CreateTransferCommand(closedTarget.source.accountId(),
                new Iban("TR269999900000000000000003"),
                new BeneficiaryName("Ayşe Yılmaz"),
                money("1.00"), TransferChannel.API, MAKER);

        assertThatThrownBy(() -> closedTarget.createService.create(toClosed))
                .isInstanceOf(AccountNotActiveException.class);
        assertThat(closedTarget.store.storedCount()).isZero();

        TransferServicesFixture empty = new TransferServicesFixture();
        empty.accounts.makeUnavailable();
        assertThatThrownBy(() -> empty.createService.create(empty.command("1.00")))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    void unknownTargetIban_isNotFound() {
        CreateTransferCommand toUnknown = new CreateTransferCommand(fixture.source.accountId(),
                new Iban("TR269999900000000000000003"),
                new BeneficiaryName("Ayşe Yılmaz"),
                money("1.00"), TransferChannel.API, MAKER);

        assertThatThrownBy(() -> fixture.createService.create(toUnknown)).isInstanceOf(AccountNotFoundException.class);
    }
}
