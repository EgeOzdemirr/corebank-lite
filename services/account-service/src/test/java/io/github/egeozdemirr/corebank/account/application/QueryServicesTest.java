package io.github.egeozdemirr.corebank.account.application;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.OPENED_AT;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.TRY;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.support.InMemoryAccountStore;
import io.github.egeozdemirr.corebank.account.support.InMemoryLedger;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import org.junit.jupiter.api.Test;

class QueryServicesTest {

    private final InMemoryAccountStore accounts = new InMemoryAccountStore();
    private final InMemoryLedger ledger = new InMemoryLedger();
    private final AccountQueryService accountQueries = new AccountQueryService(accounts, accounts);
    private final LedgerQueryService ledgerQueries = new LedgerQueryService(accounts, ledger);

    @Test
    void getAccount_andBalance_returnStoredState() {
        Account account = TestAccounts.customerAccount("42.50");
        accounts.add(account);

        assertThat(accountQueries.getAccount(account.id())).isEqualTo(account);
        AccountBalance balance = accountQueries.getBalance(account.id());
        assertThat(balance.balance()).isEqualTo(money("42.50"));
        assertThat(balance.iban()).isEqualTo(account.iban());
    }

    @Test
    void unknownAccount_isNotFoundForEveryQuery() {
        AccountId unknown = AccountId.newId();

        assertThatThrownBy(() -> accountQueries.getAccount(unknown)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> accountQueries.getBalance(unknown)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> ledgerQueries.getEntries(unknown, new PageQuery(0, 10)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getEntries_returnsOnlyTheAccountsLinesPaged() {
        Account funding = TestAccounts.fundingAccount(TRY);
        Account customer = TestAccounts.customerAccount("0.00");
        accounts.add(funding);
        accounts.add(customer);
        for (int i = 0; i < 3; i++) {
            ledger.append(Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, funding.id(),
                    customer.id(), money("10.00"), OPENED_AT.plusSeconds(i)));
        }

        PageResult<LedgerEntry> page = ledgerQueries.getEntries(customer.id(), new PageQuery(0, 2));

        assertThat(page.items()).hasSize(2).allSatisfy(entry -> assertThat(entry.accountId()).isEqualTo(customer.id()));
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.items().getFirst().postedAt()).isEqualTo(OPENED_AT.plusSeconds(2));
    }

    @Test
    void pageQuery_guardsAgainstProgrammingErrors() {
        assertThatThrownBy(() -> new PageQuery(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
