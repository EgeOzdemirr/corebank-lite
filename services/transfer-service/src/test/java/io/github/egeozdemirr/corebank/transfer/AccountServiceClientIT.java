package io.github.egeozdemirr.corebank.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.after;
import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.failWith;
import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.failWithoutErrorInfo;
import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.posted;
import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.slow;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.application.AccountServiceUnavailableException;
import io.github.egeozdemirr.corebank.transfer.application.CustomerAccount;
import io.github.egeozdemirr.corebank.transfer.application.PostingOutcome;
import io.github.egeozdemirr.corebank.transfer.application.port.AccountDirectory;
import io.github.egeozdemirr.corebank.transfer.application.port.LedgerPostingGateway;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import io.github.egeozdemirr.corebank.transfer.support.FakeAccountServiceConfiguration;
import io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService;
import io.github.egeozdemirr.corebank.transfer.support.PostgresContainerConfiguration;
import io.grpc.Status;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

/**
 * The gRPC client against a scripted account-service on the in-process transport. Budget and delays are shortened
 * so the time-related cases run in well under a second each.
 */
@SpringBootTest(properties = {
    "corebank.transfer.account-service.call-deadline=300ms",
    "corebank.transfer.account-service.posting-budget=800ms",
    "corebank.transfer.account-service.retry.max-retries=3",
    "corebank.transfer.account-service.retry.initial-delay=20ms",
    "corebank.transfer.account-service.retry.multiplier=2",
    "corebank.transfer.account-service.retry.max-delay=100ms"
})
@AutoConfigureTestGrpcTransport
@Import({PostgresContainerConfiguration.class, FakeAccountServiceConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class AccountServiceClientIT {

    private static final Duration POSTING_BUDGET = Duration.ofMillis(800);
    /** Time for the in-process call and the scheduler on a busy CI machine, beyond the budget itself. */
    private static final Duration SCHEDULING_ALLOWANCE = Duration.ofMillis(700);

    @Autowired
    private FakeLedgerService accountService;

    @Autowired
    private AccountDirectory accountDirectory;

    @Autowired
    private LedgerPostingGateway postingGateway;

    @BeforeEach
    void resetFake() {
        accountService.reset();
    }

    @Test
    void customerAccount_isFoundByIdAndIbanWithItsStatus() {
        String accountId = UUID.randomUUID().toString();
        accountService.addAccount(accountId, SOURCE_IBAN, "TRY", false);

        CustomerAccount byId = accountDirectory.findById(new AccountId(UUID.fromString(accountId)));
        CustomerAccount byIban = accountDirectory.findByIban(new Iban(SOURCE_IBAN));

        assertThat(byId).isEqualTo(byIban);
        assertThat(byId.active()).isFalse();
        assertThat(byId.reference().currency().getCurrencyCode()).isEqualTo("TRY");
    }

    @Test
    void unknownAccount_isNotFoundAndUnreachableServiceIsUnavailable() {
        assertThatThrownBy(() -> accountDirectory.findByIban(new Iban(SOURCE_IBAN)))
                .isInstanceOf(AccountNotFoundException.class)
                .message().doesNotContain(SOURCE_IBAN);

        accountService.makeLookupsUnavailable();

        assertThatThrownBy(() -> accountDirectory.findById(new AccountId(UUID.randomUUID())))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    void posting_sendsTheTransferIdAsPostingIdAndReturnsTheLedgerTime() {
        TransferId transferId = TransferId.newId();
        TransferOrder order = order("125.50");

        PostingOutcome outcome = postingGateway.post(transferId, order);

        assertThat(outcome).isEqualTo(new PostingOutcome.Posted(FakeLedgerService.POSTED_AT));
        assertThat(accountService.postingRequests()).singleElement().satisfies(request -> {
            assertThat(request.getPostingId()).isEqualTo(transferId.toString());
            assertThat(request.getDebitAccountId()).isEqualTo(order.source().accountId().toString());
            assertThat(request.getCreditAccountId()).isEqualTo(order.target().accountId().toString());
            assertThat(request.getAmount().getAmount()).isEqualTo("125.50");
        });
    }

    @Test
    void definiteRejection_isRejectedWithItsCodeAndNotRetried() {
        accountService.scriptPostings(failWith(Status.Code.FAILED_PRECONDITION, "INSUFFICIENT_FUNDS"));

        assertThat(postingGateway.post(TransferId.newId(), order("1.00")))
                .isEqualTo(new PostingOutcome.Rejected(new FailureReason("INSUFFICIENT_FUNDS")));
        assertThat(accountService.postingRequests()).hasSize(1);
    }

    @Test
    void internalOrUnknownOrUnlabelledFailure_isUnknownAndNotRetried() {
        accountService.scriptPostings(failWith(Status.Code.INTERNAL, "INTERNAL_ERROR"));
        assertThat(postingGateway.post(TransferId.newId(), order("1.00"))).isInstanceOf(PostingOutcome.Unknown.class);

        accountService.scriptPostings(failWithoutErrorInfo(Status.Code.UNKNOWN));
        assertThat(postingGateway.post(TransferId.newId(), order("1.00"))).isInstanceOf(PostingOutcome.Unknown.class);

        accountService.scriptPostings(failWithoutErrorInfo(Status.Code.FAILED_PRECONDITION));
        assertThat(postingGateway.post(TransferId.newId(), order("1.00"))).isInstanceOf(PostingOutcome.Unknown.class);

        assertThat(accountService.postingRequests()).hasSize(3);
    }

    /**
     * A status description is free text written by the other side (account-service, a proxy, the gRPC library). The
     * log line keeps only the transfer id, the status code and account-service's error code.
     */
    @Test
    void unknownOutcomeLog_carriesNoFreeTextFromTheRemoteSide(CapturedOutput output) {
        accountService.scriptPostings((request, response) -> response.onError(Status.INTERNAL
                .withDescription("row TR809999900000000000000001 of Mehmet Demir, TCKN 10000000146")
                .withCause(new IllegalStateException("Mehmet Demir")).asRuntimeException()));
        TransferId transferId = TransferId.newId();

        postingGateway.post(transferId, order("1.00"));

        assertThat(output.getOut()).contains(transferId.toString(), "INTERNAL")
                .doesNotContain(SOURCE_IBAN, "Mehmet Demir", "10000000146");
    }

    @Test
    void postingIdConflict_isReportedOnceAndNeverRetried() {
        accountService.scriptPostings(failWith(Status.Code.ALREADY_EXISTS, "POSTING_ID_CONFLICT"));

        assertThat(postingGateway.post(TransferId.newId(), order("1.00")))
                .isInstanceOf(PostingOutcome.IdConflict.class);
        assertThat(accountService.postingRequests()).hasSize(1);
    }

    @Test
    void abortedPosting_isRetriedWithBackoffUntilItSucceeds() {
        accountService.scriptPostings(failWith(Status.Code.ABORTED, "CONCURRENT_MODIFICATION"),
                failWith(Status.Code.ABORTED, "CONCURRENT_MODIFICATION"), posted());

        assertThat(postingGateway.post(TransferId.newId(), order("1.00")))
                .isEqualTo(new PostingOutcome.Posted(FakeLedgerService.POSTED_AT));
        assertThat(accountService.postingRequests()).hasSize(3)
                .extracting(request -> request.getPostingId()).containsOnly(
                        accountService.postingRequests().getFirst().getPostingId());
    }

    @Test
    void abortedPostingBeyondTheRetryLimit_isUnknown() {
        for (int attempt = 0; attempt < 5; attempt++) {
            accountService.scriptPostings(failWith(Status.Code.ABORTED, "CONCURRENT_MODIFICATION"));
        }

        assertThat(postingGateway.post(TransferId.newId(), order("1.00"))).isInstanceOf(PostingOutcome.Unknown.class);
        assertThat(accountService.postingRequests()).hasSize(4);
    }

    /** Every attempt times out; without the budget, 1 + 3 retries of 300 ms each would take over 1.2 s. */
    @Test
    void slowAccountService_isGivenUpWithinThePostingBudget() {
        for (int attempt = 0; attempt < 5; attempt++) {
            accountService.scriptPostings(slow(Duration.ofSeconds(2)));
        }
        long startedAt = System.nanoTime();

        PostingOutcome outcome = postingGateway.post(TransferId.newId(), order("1.00"));

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        assertThat(outcome).isInstanceOf(PostingOutcome.Unknown.class);
        assertThat(elapsed).isLessThan(POSTING_BUDGET.plus(SCHEDULING_ALLOWANCE));
    }

    /** Each abort arrives after 250 ms: retries would remain, but the 800 ms budget runs out first. */
    @Test
    void retriesStopWhenTheBudgetIsSpent() {
        for (int attempt = 0; attempt < 5; attempt++) {
            accountService.scriptPostings(
                    after(Duration.ofMillis(250), failWith(Status.Code.ABORTED, "CONCURRENT_MODIFICATION")));
        }

        assertThat(postingGateway.post(TransferId.newId(), order("1.00"))).isInstanceOf(PostingOutcome.Unknown.class);
        assertThat(accountService.postingRequests()).hasSizeLessThan(4);
    }
}
