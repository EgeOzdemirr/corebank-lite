package io.github.egeozdemirr.corebank.account.api.grpc;

import io.github.egeozdemirr.corebank.account.application.AccountQueryService;
import io.github.egeozdemirr.corebank.account.application.PostTransferService;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.CustomerAccount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.FindCustomerAccountByIbanRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.GetCustomerAccountRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.LedgerServiceGrpc;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC translation only, like a controller: request to command, result to message. Exceptions are not caught
 * here; {@link GrpcExceptionTranslator} turns them into statuses.
 */
@GrpcService
public class LedgerGrpcService extends LedgerServiceGrpc.LedgerServiceImplBase {

    private final AccountQueryService accountQueryService;
    private final PostTransferService postTransferService;

    public LedgerGrpcService(AccountQueryService accountQueryService, PostTransferService postTransferService) {
        this.accountQueryService = accountQueryService;
        this.postTransferService = postTransferService;
    }

    @Override
    public void getCustomerAccount(GetCustomerAccountRequest request, StreamObserver<CustomerAccount> response) {
        complete(response, LedgerGrpcMapper.toMessage(accountQueryService.getCustomerAccount(
                LedgerGrpcMapper.accountId(request.getAccountId(), "account_id"))));
    }

    @Override
    public void findCustomerAccountByIban(FindCustomerAccountByIbanRequest request,
                                          StreamObserver<CustomerAccount> response) {
        complete(response, LedgerGrpcMapper.toMessage(
                accountQueryService.getCustomerAccount(new Iban(request.getIban()))));
    }

    @Override
    public void postTransfer(PostTransferRequest request, StreamObserver<PostTransferResponse> response) {
        complete(response, LedgerGrpcMapper.toMessage(
                postTransferService.post(LedgerGrpcMapper.toCommand(request))));
    }

    private static <T> void complete(StreamObserver<T> response, T message) {
        response.onNext(message);
        response.onCompleted();
    }
}
