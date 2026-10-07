package io.github.egeozdemirr.corebank.transfer.infrastructure.grpc;

import com.google.protobuf.Timestamp;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.LedgerServiceGrpc;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.MonetaryAmount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferResponse;
import io.github.egeozdemirr.corebank.transfer.application.PostingOutcome;
import io.github.egeozdemirr.corebank.transfer.application.port.LedgerPostingGateway;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import io.grpc.Deadline;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Posts over gRPC with the transfer id as posting id, so every attempt is safe to repeat (ADR-0004). Only an aborted
 * call is retried, with exponential backoff, and all attempts together stay within the posting budget: the policy's
 * timeout stops further retries, and each call's deadline is cut to what is left of the budget (ADR-0005).
 */
@Component
class GrpcLedgerPostingGateway implements LedgerPostingGateway {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcLedgerPostingGateway.class);

    private final LedgerServiceGrpc.LedgerServiceBlockingStub ledger;
    private final AccountServiceProperties properties;
    private final RetryTemplate retryTemplate;

    GrpcLedgerPostingGateway(LedgerServiceGrpc.LedgerServiceBlockingStub ledger, AccountServiceProperties properties) {
        this.ledger = ledger;
        this.properties = properties;
        AccountServiceProperties.Retry retry = properties.retry();
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(retry.maxRetries())
                .delay(retry.initialDelay())
                .multiplier(retry.multiplier().doubleValue())
                .maxDelay(retry.maxDelay())
                .timeout(properties.postingBudget())
                .predicate(GrpcOutcomeClassifier::isRetryable)
                .build());
    }

    @Override
    public PostingOutcome post(TransferId transferId, TransferOrder order) {
        PostTransferRequest request = PostTransferRequest.newBuilder()
                .setPostingId(transferId.value().toString())
                .setDebitAccountId(order.source().accountId().value().toString())
                .setCreditAccountId(order.target().accountId().value().toString())
                .setAmount(MonetaryAmount.newBuilder()
                        .setAmount(order.amount().amount().toPlainString())
                        .setCurrency(order.amount().currency().getCurrencyCode()))
                .build();
        Deadline budget = Deadline.after(properties.postingBudget().toNanos(), TimeUnit.NANOSECONDS);
        try {
            PostTransferResponse response = retryTemplate.execute(() -> attempt(request, budget));
            return new PostingOutcome.Posted(instantOf(response.getPostedAt()));
        } catch (RetryException stopped) {
            return outcomeOf(transferId, stopped.getLastException());
        }
    }

    private PostTransferResponse attempt(PostTransferRequest request, Deadline budget) {
        Deadline callDeadline = Deadline.after(properties.callDeadline().toNanos(), TimeUnit.NANOSECONDS)
                .minimum(budget);
        return ledger.withDeadline(callDeadline).postTransfer(request);
    }

    private static PostingOutcome outcomeOf(TransferId transferId, Throwable failure) {
        if (!(failure instanceof StatusRuntimeException)) {
            LOG.warn("Posting of transfer {} ended without a gRPC status; outcome unknown", transferId, failure);
            return new PostingOutcome.Unknown(failure.getClass().getSimpleName());
        }
        GrpcOutcomeClassifier.Classification classification = GrpcOutcomeClassifier.classify(failure);
        return switch (classification.kind()) {
            case DEFINITE_REJECTION -> new PostingOutcome.Rejected(new FailureReason(classification.errorCode()
                    .orElseThrow()));
            case ID_CONFLICT -> new PostingOutcome.IdConflict();
            case RETRYABLE, UNKNOWN -> {
                LOG.warn("Posting of transfer {} has an unknown outcome: {}", transferId,
                        ((StatusRuntimeException) failure).getStatus());
                yield new PostingOutcome.Unknown(((StatusRuntimeException) failure).getStatus().getCode().name());
            }
        };
    }

    private static Instant instantOf(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
