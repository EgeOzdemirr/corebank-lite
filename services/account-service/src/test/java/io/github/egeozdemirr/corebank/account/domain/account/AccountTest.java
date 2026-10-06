package io.github.egeozdemirr.corebank.account.domain.account;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.OPENED_AT;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.TRY;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.USD;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotActiveException;
import io.github.egeozdemirr.corebank.account.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidHolderNameException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidPostingException;
import io.github.egeozdemirr.corebank.account.domain.ledger.EntryDirection;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntryId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void open_startsActiveWithZeroBalance() {
        CustomerOwner owner = TestAccounts.customerOwner();

        Account account = Account.open(AccountId.newId(), TestAccounts.nextIban(), owner, TRY, OPENED_AT);

        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(Money.zero(TRY));
        assertThat(account.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.currency()).isEqualTo(TRY);
        assertThat(account.openedAt()).isEqualTo(OPENED_AT);
        assertThat(account.owner()).isEqualTo(owner);
    }

    @Test
    void credit_increasesAndDebit_decreasesBalance() {
        Account account = TestAccounts.customerAccount("100.00");

        account.post(entry(account, EntryDirection.CREDIT, "50.00"));
        account.post(entry(account, EntryDirection.DEBIT, "30.25"));

        assertThat(account.balance()).isEqualTo(money("119.75"));
    }

    @Test
    void customerAccount_cannotGoNegative() {
        Account account = TestAccounts.customerAccount("10.00");

        assertThatThrownBy(() -> account.post(entry(account, EntryDirection.DEBIT, "10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.balance()).isEqualTo(money("10.00"));
    }

    @Test
    void customerAccount_canBeDebitedToExactlyZero() {
        Account account = TestAccounts.customerAccount("10.00");

        account.post(entry(account, EntryDirection.DEBIT, "10.00"));

        assertThat(account.balance().isZero()).isTrue();
    }

    @Test
    void fundingAccount_mayGoNegativeBecauseItsTypeAllowsIt() {
        Account funding = TestAccounts.fundingAccount(TRY);

        funding.post(entry(funding, EntryDirection.DEBIT, "1000.00"));

        assertThat(funding.type()).isEqualTo(AccountType.FUNDING);
        assertThat(funding.balance()).isEqualTo(money("-1000.00"));
    }

    @Test
    void accountType_decidesNegativeBalancePolicy() {
        assertThat(AccountType.CUSTOMER.allowsNegativeBalance()).isFalse();
        assertThat(AccountType.FUNDING.allowsNegativeBalance()).isTrue();
    }

    @Test
    void closedAccount_rejectsPostings() {
        Account account = TestAccounts.closedCustomerAccount();

        assertThatThrownBy(() -> account.post(entry(account, EntryDirection.CREDIT, "1.00")))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void entryOfAnotherAccount_isRejected() {
        Account account = TestAccounts.customerAccount("10.00");
        Account other = TestAccounts.customerAccount("10.00");

        assertThatThrownBy(() -> account.post(entry(other, EntryDirection.CREDIT, "1.00")))
                .isInstanceOf(InvalidPostingException.class);
    }

    @Test
    void entryInAnotherCurrency_isRejected() {
        Account account = TestAccounts.customerAccount("10.00");
        LedgerEntry dollarEntry = new LedgerEntry(LedgerEntryId.newId(), PostingId.newId(), account.id(),
                EntryDirection.CREDIT, Money.of("1.00", USD), PostingType.OPENING_DEPOSIT, OPENED_AT);

        assertThatThrownBy(() -> account.post(dollarEntry)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void equality_isByIdentity() {
        Account account = TestAccounts.customerAccount("10.00");
        Account sameId = Account.restore(account.id(), account.iban(), account.owner(), OPENED_AT,
                AccountStatus.ACTIVE, money("99.00"));

        assertThat(account).isEqualTo(sameId).hasSameHashCodeAs(sameId);
        assertThat(account).isNotEqualTo(TestAccounts.customerAccount("10.00"));
    }

    @Test
    void toString_doesNotLeakPersonalData() {
        Account account = TestAccounts.customerAccount("10.00");
        CustomerOwner owner = (CustomerOwner) account.owner();

        assertThat(account.toString())
                .doesNotContain(account.iban().value())
                .doesNotContain(owner.tckn().value());
        assertThat(owner.toString()).doesNotContain(owner.tckn().value());
    }

    @Test
    void holderName_isTrimmedAndBounded() {
        assertThat(new HolderName("  Ayşe Yılmaz ").value()).isEqualTo("Ayşe Yılmaz");
        assertThatThrownBy(() -> new HolderName("   ")).isInstanceOf(InvalidHolderNameException.class);
        assertThatThrownBy(() -> new HolderName("x".repeat(HolderName.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidHolderNameException.class);
    }

    @Test
    void accountIds_orderByValue() {
        AccountId lower = new AccountId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        AccountId higher = new AccountId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertThat(lower).isLessThan(higher);
        assertThat(lower.toString()).isEqualTo("00000000-0000-0000-0000-000000000001");
    }

    private static LedgerEntry entry(Account account, EntryDirection direction, String amount) {
        return new LedgerEntry(LedgerEntryId.newId(), PostingId.newId(), account.id(), direction, money(amount),
                PostingType.OPENING_DEPOSIT, OPENED_AT);
    }
}
