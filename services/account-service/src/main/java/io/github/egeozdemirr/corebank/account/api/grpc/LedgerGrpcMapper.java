package io.github.egeozdemirr.corebank.account.api.grpc;

import com.google.protobuf.Timestamp;
import io.github.egeozdemirr.corebank.account.application.PostTransferCommand;
import io.github.egeozdemirr.corebank.account.application.PostTransferResult;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.money.Currencies;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.AccountStatus;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.CustomerAccount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.MonetaryAmount;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferRequest;
import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.PostTransferResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Translation between protobuf messages and application types; no business rule lives here. */
final class LedgerGrpcMapper {

    /** Same format as the event contracts: no exponent, no rounding, exactly two fraction digits. */
    private static final Pattern AMOUNT_FORMAT = Pattern.compile("^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$");

    private LedgerGrpcMapper() {
    }

    static AccountId accountId(String value, String field) {
        return new AccountId(uuid(value, field));
    }

    static PostTransferCommand toCommand(PostTransferRequest request) {
        return new PostTransferCommand(
                new PostingId(uuid(request.getPostingId(), "posting_id")),
                accountId(request.getDebitAccountId(), "debit_account_id"),
                accountId(request.getCreditAccountId(), "credit_account_id"),
                money(request.getAmount()));
    }

    static CustomerAccount toMessage(Account account) {
        return CustomerAccount.newBuilder()
                .setAccountId(account.id().value().toString())
                .setIban(account.iban().value())
                .setCurrency(account.currency().getCurrencyCode())
                .setStatus(status(account))
                .build();
    }

    static PostTransferResponse toMessage(PostTransferResult result) {
        return PostTransferResponse.newBuilder()
                .setPostingId(result.postingId().value().toString())
                .setPostedAt(timestamp(result.postedAt()))
                .setAlreadyPosted(result.alreadyPosted())
                .build();
    }

    private static Money money(MonetaryAmount amount) {
        if (!AMOUNT_FORMAT.matcher(amount.getAmount()).matches()) {
            throw new InvalidGrpcRequestException("amount must be a decimal string with two fraction digits");
        }
        return Money.of(new BigDecimal(amount.getAmount()), Currencies.fromCode(amount.getCurrency()));
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidGrpcRequestException(field + " must be a UUID", malformed);
        }
    }

    private static AccountStatus status(Account account) {
        return switch (account.status()) {
            case ACTIVE -> AccountStatus.ACCOUNT_STATUS_ACTIVE;
            case CLOSED -> AccountStatus.ACCOUNT_STATUS_CLOSED;
        };
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
