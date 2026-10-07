package io.github.egeozdemirr.corebank.transfer.infrastructure.grpc;

import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.AccountStatus;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.FindCustomerAccountByIbanRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.GetCustomerAccountRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.LedgerServiceGrpc;
import io.github.egeozdemirr.corebank.transfer.application.AccountServiceUnavailableException;
import io.github.egeozdemirr.corebank.transfer.application.CustomerAccount;
import io.github.egeozdemirr.corebank.transfer.application.port.AccountDirectory;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.money.Currencies;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Account lookups over the internal gRPC API. Nothing is recorded yet when they run, so no retry is needed here. */
@Component
class GrpcAccountDirectory implements AccountDirectory {

    private final LedgerServiceGrpc.LedgerServiceBlockingStub ledger;
    private final AccountServiceProperties properties;

    GrpcAccountDirectory(LedgerServiceGrpc.LedgerServiceBlockingStub ledger, AccountServiceProperties properties) {
        this.ledger = ledger;
        this.properties = properties;
    }

    @Override
    public CustomerAccount findById(AccountId accountId) {
        return lookUp(() -> withDeadline().getCustomerAccount(GetCustomerAccountRequest.newBuilder()
                .setAccountId(accountId.value().toString()).build()), () -> new AccountNotFoundException(accountId));
    }

    @Override
    public CustomerAccount findByIban(Iban iban) {
        return lookUp(() -> withDeadline().findCustomerAccountByIban(FindCustomerAccountByIbanRequest.newBuilder()
                .setIban(iban.value()).build()), () -> new AccountNotFoundException(iban));
    }

    /** The message type of the gRPC contract has the same simple name as the application's view of an account. */
    private CustomerAccount lookUp(
            Supplier<io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.CustomerAccount> call,
            Supplier<AccountNotFoundException> notFound) {
        try {
            return toApplication(call.get());
        } catch (StatusRuntimeException failure) {
            GrpcOutcomeClassifier.Classification classification = GrpcOutcomeClassifier.classify(failure);
            boolean definitelyUnknownAccount = classification.kind() == GrpcOutcomeClassifier.Kind.DEFINITE_REJECTION
                    && failure.getStatus().getCode() == Status.Code.NOT_FOUND;
            if (definitelyUnknownAccount) {
                AccountNotFoundException unknownAccount = notFound.get();
                unknownAccount.initCause(failure);
                throw unknownAccount;
            }
            throw new AccountServiceUnavailableException("Account lookup failed: " + failure.getStatus().getCode(),
                    failure);
        }
    }

    private LedgerServiceGrpc.LedgerServiceBlockingStub withDeadline() {
        return ledger.withDeadlineAfter(properties.callDeadline().toNanos(), TimeUnit.NANOSECONDS);
    }

    private static CustomerAccount toApplication(
            io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.CustomerAccount account) {
        AccountReference reference = new AccountReference(new AccountId(UUID.fromString(account.getAccountId())),
                new Iban(account.getIban()), Currencies.fromCode(account.getCurrency()));
        return new CustomerAccount(reference,
                account.getStatus() == AccountStatus.ACCOUNT_STATUS_ACTIVE);
    }
}
