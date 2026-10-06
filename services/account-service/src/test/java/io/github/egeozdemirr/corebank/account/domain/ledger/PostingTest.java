package io.github.egeozdemirr.corebank.account.domain.ledger;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.OPENED_AT;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.USD;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidAmountException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidPostingException;
import io.github.egeozdemirr.corebank.account.domain.exception.UnbalancedPostingException;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostingTest {

    private final AccountId first = new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private final AccountId second = new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    @Test
    void between_producesOneDebitAndOneCreditThatSumToZero() {
        Posting posting = Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, first, second,
                money("250.00"), OPENED_AT);

        assertThat(posting.entries()).hasSize(2)
                .extracting(LedgerEntry::direction)
                .containsExactly(EntryDirection.DEBIT, EntryDirection.CREDIT);
        Money total = posting.entries().stream().map(LedgerEntry::signedAmount)
                .reduce(Money.zero(money("0").currency()), Money::plus);
        assertThat(total.isZero()).isTrue();
        assertThat(posting.entries()).allSatisfy(entry -> assertThat(entry.postingId()).isEqualTo(posting.id()));
    }

    @Test
    void accountIdsInLockOrder_areSortedAndDistinct() {
        Posting posting = Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, second, first,
                money("1.00"), OPENED_AT);

        assertThat(posting.accountIdsInLockOrder()).containsExactly(first, second);
    }

    @Test
    void between_rejectsSameAccountOnBothSides() {
        assertThatThrownBy(() -> Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, first, first,
                money("1.00"), OPENED_AT)).isInstanceOf(InvalidPostingException.class);
    }

    @Test
    void between_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, first, second,
                money("0.00"), OPENED_AT)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void of_rejectsUnbalancedEntries() {
        PostingId id = PostingId.newId();
        List<LedgerEntry> entries = List.of(
                entry(id, first, EntryDirection.DEBIT, money("100.00")),
                entry(id, second, EntryDirection.CREDIT, money("99.99")));

        assertThatThrownBy(() -> Posting.of(id, entries))
                .isInstanceOf(UnbalancedPostingException.class)
                .hasMessageContaining("-0.01 TRY");
    }

    @Test
    void of_acceptsBalancedMultiLegPosting() {
        PostingId id = PostingId.newId();
        AccountId third = AccountId.newId();

        Posting posting = Posting.of(id, List.of(
                entry(id, first, EntryDirection.DEBIT, money("100.00")),
                entry(id, second, EntryDirection.CREDIT, money("60.00")),
                entry(id, third, EntryDirection.CREDIT, money("40.00"))));

        assertThat(posting.accountIdsInLockOrder()).hasSize(3);
    }

    @Test
    void of_rejectsSingleEntry() {
        PostingId id = PostingId.newId();

        assertThatThrownBy(() -> Posting.of(id, List.of(entry(id, first, EntryDirection.DEBIT, money("1.00")))))
                .isInstanceOf(InvalidPostingException.class);
    }

    @Test
    void of_rejectsEntriesOfAnotherPosting() {
        PostingId id = PostingId.newId();
        List<LedgerEntry> entries = List.of(
                entry(id, first, EntryDirection.DEBIT, money("1.00")),
                entry(PostingId.newId(), second, EntryDirection.CREDIT, money("1.00")));

        assertThatThrownBy(() -> Posting.of(id, entries)).isInstanceOf(InvalidPostingException.class);
    }

    @Test
    void of_rejectsMixedCurrencies() {
        PostingId id = PostingId.newId();
        List<LedgerEntry> entries = List.of(
                entry(id, first, EntryDirection.DEBIT, money("1.00")),
                entry(id, second, EntryDirection.CREDIT, Money.of("1.00", USD)));

        assertThatThrownBy(() -> Posting.of(id, entries)).isInstanceOf(CurrencyMismatchException.class);
    }

    private static LedgerEntry entry(PostingId postingId, AccountId accountId, EntryDirection direction,
                                     Money amount) {
        return new LedgerEntry(LedgerEntryId.newId(), postingId, accountId, direction, amount,
                PostingType.OPENING_DEPOSIT, OPENED_AT);
    }
}
