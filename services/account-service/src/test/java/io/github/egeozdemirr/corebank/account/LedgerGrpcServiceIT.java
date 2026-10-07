package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.ErrorInfo;
import io.github.egeozdemirr.corebank.account.application.OpenAccountCommand;
import io.github.egeozdemirr.corebank.account.application.OpenAccountService;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.AccountStatus;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.CustomerAccount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.FindCustomerAccountByIbanRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.GetCustomerAccountRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.LedgerServiceGrpc;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.MonetaryAmount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The internal posting API end to end: generated stub, in-process transport, service layer and PostgreSQL. */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = LedgerServiceGrpc.LedgerServiceBlockingStub.class)
@Import(PostgresContainerConfiguration.class)
class LedgerGrpcServiceIT {

    private static final String TRY_FUNDING_ACCOUNT = "00000000-0000-4000-8000-000000000001";
    private static final String TRY_FUNDING_IBAN = "TR809999900000000000000001";
    private static final int PARALLEL_RETRIES = 16;

    @Autowired
    private LedgerServiceGrpc.LedgerServiceBlockingStub ledger;

    @Autowired
    private OpenAccountService openAccountService;

    @Autowired
    private JdbcClient jdbcClient;

    private Account payer;
    private Account payee;

    @BeforeEach
    void openAccounts() {
        payer = open("100.00");
        payee = open("0.00");
    }

    @Test
    void customerAccount_isFoundByIdAndByIban() {
        CustomerAccount byId = ledger.getCustomerAccount(GetCustomerAccountRequest.newBuilder()
                .setAccountId(payer.id().toString()).build());
        CustomerAccount byIban = ledger.findCustomerAccountByIban(FindCustomerAccountByIbanRequest.newBuilder()
                .setIban(payer.iban().value()).build());

        assertThat(byId).isEqualTo(byIban);
        assertThat(byId.getIban()).isEqualTo(payer.iban().value());
        assertThat(byId.getCurrency()).isEqualTo("TRY");
        assertThat(byId.getStatus()).isEqualTo(AccountStatus.ACCOUNT_STATUS_ACTIVE);
    }

    @Test
    void fundingAccount_isNotATransferEndpoint() {
        assertRejected(() -> ledger.getCustomerAccount(GetCustomerAccountRequest.newBuilder()
                .setAccountId(TRY_FUNDING_ACCOUNT).build()), Status.Code.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertRejected(() -> ledger.findCustomerAccountByIban(FindCustomerAccountByIbanRequest.newBuilder()
                .setIban(TRY_FUNDING_IBAN).build()), Status.Code.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertRejected(() -> ledger.postTransfer(transfer(UUID.randomUUID(), TRY_FUNDING_ACCOUNT,
                payee.id().toString(), "1.00")), Status.Code.FAILED_PRECONDITION, "ACCOUNT_NOT_ELIGIBLE");
    }

    @Test
    void postTransfer_movesMoneyOnce() {
        UUID postingId = UUID.randomUUID();

        PostTransferResponse response = ledger.postTransfer(transfer(postingId, "40.00"));

        assertThat(response.getPostingId()).isEqualTo(postingId.toString());
        assertThat(response.getAlreadyPosted()).isFalse();
        assertThat(balanceOf(payer)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(payee)).isEqualByComparingTo("40.00");
    }

    @Test
    void retriedPostTransfer_returnsTheOriginalResult() {
        UUID postingId = UUID.randomUUID();
        PostTransferResponse first = ledger.postTransfer(transfer(postingId, "40.00"));

        PostTransferResponse retry = ledger.postTransfer(transfer(postingId, "40.00"));

        assertThat(retry.getAlreadyPosted()).isTrue();
        assertThat(retry.getPostedAt()).isEqualTo(first.getPostedAt());
        assertThat(balanceOf(payer)).isEqualByComparingTo("60.00");
        assertThat(entriesOf(postingId)).isEqualTo(2);
    }

    /** What a timeout retry looks like: the same request arrives again while the first one may still run. */
    @Test
    void concurrentRetriesOfOnePosting_moveMoneyExactlyOnce() throws Exception {
        UUID postingId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PostTransferResponse>> calls = new ArrayList<>();

        try (ExecutorService clients = Executors.newFixedThreadPool(PARALLEL_RETRIES)) {
            for (int i = 0; i < PARALLEL_RETRIES; i++) {
                calls.add(clients.submit(() -> {
                    start.await();
                    return ledger.postTransfer(transfer(postingId, "25.00"));
                }));
            }
            start.countDown();
            List<Boolean> alreadyPosted = new ArrayList<>();
            for (Future<PostTransferResponse> call : calls) {
                alreadyPosted.add(call.get().getAlreadyPosted());
            }
            assertThat(alreadyPosted).containsOnlyOnce(false).hasSize(PARALLEL_RETRIES);
        }
        assertThat(balanceOf(payer)).isEqualByComparingTo("75.00");
        assertThat(balanceOf(payee)).isEqualByComparingTo("25.00");
        assertThat(entriesOf(postingId)).isEqualTo(2);
    }

    /** Same id, different content: a conflict, never "already posted", and the recorded posting stays as it was. */
    @Test
    void reusedPostingIdWithDifferentContent_isConflictNotAlreadyPosted() {
        UUID postingId = UUID.randomUUID();
        Account otherPayee = open("0.00");
        ledger.postTransfer(transfer(postingId, "40.00"));

        List<PostTransferRequest> differentContent = List.of(
                transfer(postingId, "41.00"),
                transfer(postingId, payer.id().toString(), otherPayee.id().toString(), "40.00"),
                transfer(postingId, payee.id().toString(), payer.id().toString(), "40.00"),
                withCurrency(transfer(postingId, "40.00"), "USD"));

        for (PostTransferRequest request : differentContent) {
            assertRejected(() -> ledger.postTransfer(request), Status.Code.ALREADY_EXISTS, "POSTING_ID_CONFLICT");
        }
        assertThat(balanceOf(payer)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(payee)).isEqualByComparingTo("40.00");
        assertThat(balanceOf(otherPayee)).isEqualByComparingTo("0.00");
        assertThat(entriesOf(postingId)).isEqualTo(2);
    }

    /**
     * The claim is the first write of a posting and the business check comes after it. If they were in different
     * transactions, a rejected request would leave its id claimed and a later valid request with that id would be
     * reported as already posted without any money moving.
     */
    @Test
    void rejectedPosting_rollsBackItsClaimWithTheRestOfTheTransaction() {
        UUID postingId = UUID.randomUUID();
        assertRejected(() -> ledger.postTransfer(transfer(postingId, "100.01")),
                Status.Code.FAILED_PRECONDITION, "INSUFFICIENT_FUNDS");

        PostTransferResponse retry = ledger.postTransfer(transfer(postingId, "100.00"));

        assertThat(retry.getAlreadyPosted()).isFalse();
        assertThat(balanceOf(payer)).isEqualByComparingTo("0.00");
        assertThat(entriesOf(postingId)).isEqualTo(2);
    }

    @Test
    void businessRejection_isDefiniteAndLeavesNoTrace() {
        UUID postingId = UUID.randomUUID();

        assertRejected(() -> ledger.postTransfer(transfer(postingId, "100.01")),
                Status.Code.FAILED_PRECONDITION, "INSUFFICIENT_FUNDS");
        assertRejected(() -> ledger.postTransfer(transfer(UUID.randomUUID(), payer.id().toString(),
                UUID.randomUUID().toString(), "1.00")), Status.Code.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM posting WHERE id = :id").param("id", postingId)
                .query(Long.class).single()).isZero();
        assertThat(balanceOf(payer)).isEqualByComparingTo("100.00");
    }

    /** The rows of the ADR-0004 error table that a transfer can reach today, end to end. */
    @Test
    void documentedRejections_carryTheirStatusAndErrorCode() {
        assertRejected(() -> ledger.postTransfer(withCurrency(transfer(UUID.randomUUID(), "1.00"), "XYZ")),
                Status.Code.INVALID_ARGUMENT, "UNSUPPORTED_CURRENCY");
        assertRejected(() -> ledger.postTransfer(transfer(UUID.randomUUID(), "0.00")),
                Status.Code.INVALID_ARGUMENT, "INVALID_AMOUNT");
        assertRejected(() -> ledger.postTransfer(withCurrency(transfer(UUID.randomUUID(), "1.00"), "USD")),
                Status.Code.INVALID_ARGUMENT, "CURRENCY_MISMATCH");
        assertRejected(() -> ledger.postTransfer(transfer(UUID.randomUUID(), payer.id().toString(),
                payer.id().toString(), "1.00")), Status.Code.FAILED_PRECONDITION, "INVALID_POSTING");
        assertThat(balanceOf(payer)).isEqualByComparingTo("100.00");
    }

    @Test
    void malformedRequests_areInvalidArguments() {
        assertRejected(() -> ledger.postTransfer(transfer(UUID.randomUUID(), "10.5")),
                Status.Code.INVALID_ARGUMENT, "INVALID_REQUEST");
        assertRejected(() -> ledger.postTransfer(transfer(UUID.randomUUID(), "1e3")),
                Status.Code.INVALID_ARGUMENT, "INVALID_REQUEST");
        assertRejected(() -> ledger.getCustomerAccount(GetCustomerAccountRequest.newBuilder()
                .setAccountId("not-a-uuid").build()), Status.Code.INVALID_ARGUMENT, "INVALID_REQUEST");
        assertRejected(() -> ledger.findCustomerAccountByIban(FindCustomerAccountByIbanRequest.newBuilder()
                .setIban("TR000000000000000000000000").build()), Status.Code.INVALID_ARGUMENT, "INVALID_IBAN");
    }

    private Account open(String deposit) {
        return openAccountService.open(new OpenAccountCommand(CustomerId.newId(),
                new HolderName(SyntheticData.fullName()), new Tckn(SyntheticData.tckn()), TestAccounts.TRY,
                TestAccounts.money(deposit)));
    }

    private PostTransferRequest transfer(UUID postingId, String amount) {
        return transfer(postingId, payer.id().toString(), payee.id().toString(), amount);
    }

    private static PostTransferRequest transfer(UUID postingId, String debit, String credit, String amount) {
        return PostTransferRequest.newBuilder()
                .setPostingId(postingId.toString())
                .setDebitAccountId(debit)
                .setCreditAccountId(credit)
                .setAmount(MonetaryAmount.newBuilder().setAmount(amount).setCurrency("TRY"))
                .build();
    }

    private static PostTransferRequest withCurrency(PostTransferRequest request, String currency) {
        return request.toBuilder()
                .setAmount(request.getAmount().toBuilder().setCurrency(currency))
                .build();
    }

    private BigDecimal balanceOf(Account account) {
        return jdbcClient.sql("SELECT balance FROM account WHERE id = :id")
                .param("id", account.id().value()).query(BigDecimal.class).single();
    }

    private long entriesOf(UUID postingId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM ledger_entry WHERE posting_id = :id")
                .param("id", postingId).query(Long.class).single();
    }

    private static void assertRejected(ThrowingCallable call, Status.Code code, String errorCode) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(StatusRuntimeException.class, rejection -> {
                    assertThat(rejection.getStatus().getCode()).isEqualTo(code);
                    assertThat(errorInfo(rejection).getReason()).isEqualTo(errorCode);
                });
    }

    private static ErrorInfo errorInfo(StatusRuntimeException rejection) {
        try {
            return StatusProto.fromThrowable(rejection).getDetails(0).unpack(ErrorInfo.class);
        } catch (InvalidProtocolBufferException notErrorInfo) {
            throw new AssertionError("first status detail is not an ErrorInfo", notErrorInfo);
        }
    }
}
