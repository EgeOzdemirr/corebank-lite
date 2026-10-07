package io.github.egeozdemirr.corebank.transfer.support;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.rpc.ErrorInfo;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.AccountStatus;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.CustomerAccount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.FindCustomerAccountByIbanRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.GetCustomerAccountRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.LedgerServiceGrpc;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stand-in for account-service's ledger API. Tests register accounts and script how the next PostTransfer calls are
 * answered; once the script is empty every call is posted. Errors carry ErrorInfo like the real translator does.
 */
public class FakeLedgerService extends LedgerServiceGrpc.LedgerServiceImplBase {

    public static final Instant POSTED_AT = Instant.parse("2026-10-07T09:30:05Z");
    private static final String ERROR_DOMAIN = "account-service";

    private final Map<String, CustomerAccount> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<PostingAnswer> postingScript = new ConcurrentLinkedDeque<>();
    private final List<PostTransferRequest> postingRequests = new CopyOnWriteArrayList<>();
    private volatile boolean lookupsUnavailable;

    /** How the fake answers one PostTransfer call. */
    @FunctionalInterface
    public interface PostingAnswer {
        void answer(PostTransferRequest request, StreamObserver<PostTransferResponse> response);
    }

    public void reset() {
        accountsById.clear();
        postingScript.clear();
        postingRequests.clear();
        lookupsUnavailable = false;
    }

    public void addAccount(String accountId, String iban, String currency, boolean active) {
        accountsById.put(accountId, CustomerAccount.newBuilder().setAccountId(accountId).setIban(iban)
                .setCurrency(currency)
                .setStatus(active ? AccountStatus.ACCOUNT_STATUS_ACTIVE : AccountStatus.ACCOUNT_STATUS_CLOSED)
                .build());
    }

    public void makeLookupsUnavailable() {
        lookupsUnavailable = true;
    }

    public void scriptPostings(PostingAnswer... answers) {
        postingScript.addAll(List.of(answers));
    }

    public List<PostTransferRequest> postingRequests() {
        return List.copyOf(postingRequests);
    }

    public static PostingAnswer posted() {
        return (request, response) -> complete(response, PostTransferResponse.newBuilder()
                .setPostingId(request.getPostingId())
                .setPostedAt(Timestamp.newBuilder()
                        .setSeconds(POSTED_AT.getEpochSecond())
                        .setNanos(POSTED_AT.getNano()))
                .build());
    }

    /** As the real GrpcExceptionTranslator answers: a status code plus ErrorInfo from account-service. */
    public static PostingAnswer failWith(Status.Code code, String errorCode) {
        return (request, response) -> response.onError(withErrorInfo(code, errorCode));
    }

    /** As the gRPC library or a proxy answers: a status code without any ErrorInfo. */
    public static PostingAnswer failWithoutErrorInfo(Status.Code code) {
        return (request, response) -> response.onError(code.toStatus().asRuntimeException());
    }

    /** Answers only after {@code delay}, long after a short deadline has passed. */
    public static PostingAnswer slow(Duration delay) {
        return after(delay, posted());
    }

    /** Waits for {@code delay}, then answers as {@code answer} would. */
    public static PostingAnswer after(Duration delay, PostingAnswer answer) {
        return (request, response) -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            answer.answer(request, response);
        };
    }

    @Override
    public void getCustomerAccount(GetCustomerAccountRequest request, StreamObserver<CustomerAccount> response) {
        answerLookup(Optional.ofNullable(accountsById.get(request.getAccountId())), response);
    }

    @Override
    public void findCustomerAccountByIban(FindCustomerAccountByIbanRequest request,
                                          StreamObserver<CustomerAccount> response) {
        answerLookup(accountsById.values().stream()
                .filter(account -> account.getIban().equals(request.getIban())).findFirst(), response);
    }

    @Override
    public void postTransfer(PostTransferRequest request, StreamObserver<PostTransferResponse> response) {
        postingRequests.add(request);
        PostingAnswer answer = Optional.ofNullable(postingScript.pollFirst()).orElse(posted());
        answer.answer(request, response);
    }

    private void answerLookup(Optional<CustomerAccount> account, StreamObserver<CustomerAccount> response) {
        if (lookupsUnavailable) {
            response.onError(Status.UNAVAILABLE.asRuntimeException());
            return;
        }
        account.ifPresentOrElse(found -> complete(response, found),
                () -> response.onError(withErrorInfo(Status.Code.NOT_FOUND, "ACCOUNT_NOT_FOUND")));
    }

    private static StatusRuntimeException withErrorInfo(Status.Code code, String errorCode) {
        return StatusProto.toStatusRuntimeException(com.google.rpc.Status.newBuilder()
                .setCode(code.value())
                .setMessage(errorCode)
                .addDetails(Any.pack(ErrorInfo.newBuilder().setReason(errorCode).setDomain(ERROR_DOMAIN).build()))
                .build());
    }

    private static <T> void complete(StreamObserver<T> response, T message) {
        response.onNext(message);
        response.onCompleted();
    }
}
