package io.github.egeozdemirr.corebank.account.application;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.OPENED_AT;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.support.InMemoryAccountStore;
import io.github.egeozdemirr.corebank.account.support.InMemoryLedger;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import org.junit.jupiter.api.Test;

class LedgerPostingServiceTest {

    private final InMemoryAccountStore accounts = new InMemoryAccountStore();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final LedgerPostingService service = new LedgerPostingService(accounts, accounts, ledger);

    @Test
    void post_movesMoneyAndAppendsPosting() {
        Account payer = TestAccounts.customerAccount("100.00");
        Account payee = TestAccounts.customerAccount("5.00");
        accounts.add(payer);
        accounts.add(payee);
        Posting posting = posting(payer.id(), payee.id(), "40.00");

        service.post(posting);

        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("60.00"));
        assertThat(accounts.stored(payee.id()).balance()).isEqualTo(money("45.00"));
        assertThat(ledger.postings()).containsExactly(posting);
    }

    @Test
    void post_updatesAccountsInLockOrder() {
        Account payer = TestAccounts.customerAccount("100.00");
        Account payee = TestAccounts.customerAccount("0.00");
        accounts.add(payer);
        accounts.add(payee);
        Posting posting = posting(payer.id(), payee.id(), "1.00");

        service.post(posting);

        assertThat(accounts.updateOrder()).containsExactlyElementsOf(posting.accountIdsInLockOrder());
    }

    @Test
    void post_withInsufficientFunds_changesNothing() {
        Account payer = TestAccounts.customerAccount("10.00");
        Account payee = TestAccounts.customerAccount("0.00");
        accounts.add(payer);
        accounts.add(payee);

        assertThatThrownBy(() -> service.post(posting(payer.id(), payee.id(), "10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("10.00"));
        assertThat(accounts.updateOrder()).isEmpty();
        assertThat(ledger.postings()).isEmpty();
    }

    @Test
    void post_toUnknownAccount_isRejected() {
        Account payer = TestAccounts.customerAccount("10.00");
        accounts.add(payer);

        assertThatThrownBy(() -> service.post(posting(payer.id(), AccountId.newId(), "1.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private static Posting posting(AccountId debited, AccountId credited, String amount) {
        return Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, debited, credited, money(amount),
                OPENED_AT);
    }
}
