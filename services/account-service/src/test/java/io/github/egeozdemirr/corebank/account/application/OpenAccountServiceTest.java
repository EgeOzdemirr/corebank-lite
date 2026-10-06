package io.github.egeozdemirr.corebank.account.application;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.TRY;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.USD;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountStatus;
import io.github.egeozdemirr.corebank.account.domain.account.AccountType;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.event.AccountOpened;
import io.github.egeozdemirr.corebank.account.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidAmountException;
import io.github.egeozdemirr.corebank.account.domain.exception.UnsupportedCurrencyException;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.ledger.EntryDirection;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import io.github.egeozdemirr.corebank.account.support.InMemoryAccountStore;
import io.github.egeozdemirr.corebank.account.support.InMemoryLedger;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private final InMemoryAccountStore accounts = new InMemoryAccountStore();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final List<AccountOpened> publishedEvents = new ArrayList<>();
    private Account fundingAccount;
    private OpenAccountService service;

    @BeforeEach
    void setUp() {
        fundingAccount = TestAccounts.fundingAccount(TRY);
        accounts.add(fundingAccount);
        LedgerPostingService postingService = new LedgerPostingService(accounts, accounts, ledger);
        service = new OpenAccountService(accounts, accounts, TestAccounts::nextIban, publishedEvents::add,
                postingService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void open_withoutDeposit_createsEmptyActiveAccountAndNoPosting() {
        Account account = service.open(command("0"));

        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.balance()).isEqualTo(Money.zero(TRY));
        assertThat(account.openedAt()).isEqualTo(NOW);
        assertThat(ledger.postings()).isEmpty();
    }

    @Test
    void open_publishesAccountOpenedWithoutTckn() {
        OpenAccountCommand command = command("0");

        Account account = service.open(command);

        assertThat(publishedEvents).singleElement().satisfies(event -> {
            assertThat(event.accountId()).isEqualTo(account.id());
            assertThat(event.iban()).isEqualTo(account.iban());
            assertThat(event.customerId()).isEqualTo(command.customerId());
            assertThat(event.holderName()).isEqualTo(command.holderName());
            assertThat(event.currency()).isEqualTo(TRY);
            assertThat(event.openedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void open_withDeposit_postsBalancedEntryFromFundingAccount() {
        Account account = service.open(command("1500.00"));

        assertThat(account.balance()).isEqualTo(money("1500.00"));
        assertThat(accounts.stored(fundingAccount.id()).balance()).isEqualTo(money("-1500.00"));

        Posting posting = ledger.postings().getFirst();
        assertThat(posting.entries()).extracting(LedgerEntry::postingType).containsOnly(PostingType.OPENING_DEPOSIT);
        assertThat(posting.entries())
                .filteredOn(entry -> entry.direction() == EntryDirection.DEBIT)
                .singleElement()
                .satisfies(entry -> assertThat(entry.accountId()).isEqualTo(fundingAccount.id()));
        assertThat(posting.entries())
                .filteredOn(entry -> entry.direction() == EntryDirection.CREDIT)
                .singleElement()
                .satisfies(entry -> assertThat(entry.accountId()).isEqualTo(account.id()));
    }

    @Test
    void open_inCurrencyWithoutFundingAccount_isRejected() {
        OpenAccountCommand command = new OpenAccountCommand(CustomerId.newId(), new HolderName("Ali Kaya"),
                new Tckn(SyntheticData.tckn()), USD, Money.zero(USD));

        assertThatThrownBy(() -> service.open(command)).isInstanceOf(UnsupportedCurrencyException.class);
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void command_rejectsNegativeDeposit() {
        assertThatThrownBy(() -> command("-0.01")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void command_rejectsDepositInAnotherCurrency() {
        assertThatThrownBy(() -> new OpenAccountCommand(CustomerId.newId(), new HolderName("Ali Kaya"),
                new Tckn(SyntheticData.tckn()), TRY, Money.of("1.00", USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    private static OpenAccountCommand command(String deposit) {
        return new OpenAccountCommand(CustomerId.newId(), new HolderName(SyntheticData.fullName()),
                new Tckn(SyntheticData.tckn()), TRY, money(deposit));
    }
}
