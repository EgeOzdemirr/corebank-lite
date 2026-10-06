package io.github.egeozdemirr.corebank.account.domain.ledger;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidPostingException;
import io.github.egeozdemirr.corebank.account.domain.exception.UnbalancedPostingException;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A balanced set of ledger entries recorded together. Invariant: at least two lines, one currency, every line
 * belongs to this posting, and the signed amounts sum to zero (double-entry bookkeeping).
 */
public final class Posting {

    private static final int MINIMUM_ENTRIES = 2;

    private final PostingId id;
    private final List<LedgerEntry> entries;

    private Posting(PostingId id, List<LedgerEntry> entries) {
        this.id = Objects.requireNonNull(id, "id");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        requireEnoughEntries();
        requireEntriesOfThisPosting();
        requireBalanced();
    }

    /** Validates and wraps entries that were built elsewhere, for example a multi-leg posting. */
    public static Posting of(PostingId id, List<LedgerEntry> entries) {
        return new Posting(id, entries);
    }

    /** Moves {@code amount} from the debited account to the credited account. */
    public static Posting between(PostingId id, PostingType type, AccountId debitedAccount, AccountId creditedAccount,
                                  Money amount, Instant postedAt) {
        if (debitedAccount.equals(creditedAccount)) {
            throw new InvalidPostingException("A posting cannot debit and credit the same account " + debitedAccount);
        }
        return new Posting(id, List.of(
                new LedgerEntry(LedgerEntryId.newId(), id, debitedAccount, EntryDirection.DEBIT, amount, type,
                        postedAt),
                new LedgerEntry(LedgerEntryId.newId(), id, creditedAccount, EntryDirection.CREDIT, amount, type,
                        postedAt)));
    }

    public PostingId id() {
        return id;
    }

    public List<LedgerEntry> entries() {
        return entries;
    }

    /** Distinct accounts of this posting in lock order (see {@link AccountId#compareTo}). */
    public List<AccountId> accountIdsInLockOrder() {
        return entries.stream().map(LedgerEntry::accountId).distinct().sorted().toList();
    }

    private void requireEnoughEntries() {
        if (entries.size() < MINIMUM_ENTRIES) {
            throw new InvalidPostingException("Posting " + id + " needs at least " + MINIMUM_ENTRIES + " entries");
        }
    }

    private void requireEntriesOfThisPosting() {
        boolean foreignEntry = entries.stream().anyMatch(entry -> !entry.postingId().equals(id));
        if (foreignEntry) {
            throw new InvalidPostingException("Posting " + id + " contains entries of another posting");
        }
    }

    private void requireBalanced() {
        Money first = entries.getFirst().amount();
        Money sum = entries.stream()
                .map(LedgerEntry::signedAmount)
                .reduce(Money.zero(first.currency()), Money::plus);
        if (!sum.isZero()) {
            throw new UnbalancedPostingException(id, sum);
        }
    }
}
