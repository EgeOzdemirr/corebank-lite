package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.Objects;

/** Moves {@code amount} from one customer account to another; {@code postingId} makes the request idempotent. */
public record PostTransferCommand(PostingId postingId, AccountId debitAccount, AccountId creditAccount, Money amount) {

    public PostTransferCommand {
        Objects.requireNonNull(postingId, "postingId");
        Objects.requireNonNull(debitAccount, "debitAccount");
        Objects.requireNonNull(creditAccount, "creditAccount");
        Objects.requireNonNull(amount, "amount").requirePositive();
    }
}
