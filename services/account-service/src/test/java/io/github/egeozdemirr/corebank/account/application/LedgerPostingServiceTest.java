package io.github.egeozdemirr.corebank.account.application;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.OPENED_AT;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotActiveException;
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

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryAccountStore accounts = new InMemoryAccountStore(ledger);
    private final LedgerPostingService service = new LedgerPostingService(accounts, accounts, ledger);

    @Test
    void post_movesMoneyAndAppendsPosting() {
        Account payer = TestAccounts.customerAccount("100.00");
        Account payee = TestAccounts.customerAccount("5.00");
        accounts.add(payer);
        accounts.add(payee);
        Posting posting = posting(payer.id(), payee.id(), "40.00");

        assertThat(service.post(posting)).isEqualTo(PostingOutcome.POSTED);

        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("60.00"));
        assertThat(accounts.stored(payee.id()).balance()).isEqualTo(money("45.00"));
        assertThat(ledger.postings()).containsExactly(posting);
    }

    @Test
    void post_locksAndUpdatesAccountsInLockOrder() {
        Account payer = TestAccounts.customerAccount("100.00");
        Account payee = TestAccounts.customerAccount("0.00");
        accounts.add(payer);
        accounts.add(payee);
        Posting posting = posting(payer.id(), payee.id(), "1.00");

        service.post(posting);

        assertThat(accounts.lockOrder()).containsExactlyElementsOf(posting.accountIdsInLockOrder());
        assertThat(accounts.updateOrder()).containsExactlyElementsOf(posting.accountIdsInLockOrder());
    }

    @Test
    void post_fromFundingAccount_neitherLocksNorUpdatesIt() {
        Account funding = TestAccounts.fundingAccount(TestAccounts.TRY);
        Account customer = TestAccounts.customerAccount("0.00");
        accounts.add(funding);
        accounts.add(customer);

        service.post(Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, funding.id(), customer.id(),
                money("250.00"), OPENED_AT));

        assertThat(accounts.lockOrder()).containsExactly(customer.id());
        assertThat(accounts.updateOrder()).containsExactly(customer.id());
        assertThat(accounts.stored(customer.id()).balance()).isEqualTo(money("250.00"));
        assertThat(accounts.findBalance(funding.id())).get()
                .extracting(AccountBalance::balance).isEqualTo(money("-250.00"));
    }

    @Test
    void post_toClosedAccount_changesNothing() {
        Account payer = TestAccounts.customerAccount("10.00");
        Account closed = TestAccounts.closedCustomerAccount();
        accounts.add(payer);
        accounts.add(closed);

        assertThatThrownBy(() -> service.post(posting(payer.id(), closed.id(), "1.00")))
                .isInstanceOf(AccountNotActiveException.class);
        assertThat(accounts.updateOrder()).isEmpty();
        assertThat(ledger.postings()).isEmpty();
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
    void post_withAlreadyRecordedId_changesNothing() {
        Account payer = TestAccounts.customerAccount("100.00");
        Account payee = TestAccounts.customerAccount("0.00");
        accounts.add(payer);
        accounts.add(payee);
        Posting posting = posting(payer.id(), payee.id(), "10.00");
        service.post(posting);

        PostingOutcome outcome = service.post(posting);

        assertThat(outcome).isEqualTo(PostingOutcome.ALREADY_RECORDED);
        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("90.00"));
        assertThat(ledger.postings()).hasSize(1);
    }

    @Test
    void post_toUnknownAccount_isRejected() {
        Account payer = TestAccounts.customerAccount("10.00");
        accounts.add(payer);

        assertThatThrownBy(() -> service.post(posting(payer.id(), AccountId.newId(), "1.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private static Posting posting(AccountId debited, AccountId credited, String amount) {
        return Posting.between(PostingId.newId(), PostingType.TRANSFER, debited, credited, money(amount), OPENED_AT);
    }
}
