package io.github.egeozdemirr.corebank.account.domain.ledger;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.time.Instant;
import java.util.Objects;

/** One immutable line of the ledger. The amount is always positive; the direction carries the sign. */
public record LedgerEntry(
        LedgerEntryId id,
        PostingId postingId,
        AccountId accountId,
        EntryDirection direction,
        Money amount,
        PostingType postingType,
        Instant postedAt) {

    public LedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postingId, "postingId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount").requirePositive();
        Objects.requireNonNull(postingType, "postingType");
        Objects.requireNonNull(postedAt, "postedAt");
    }

    public Money signedAmount() {
        return direction.signed(amount);
    }
}
