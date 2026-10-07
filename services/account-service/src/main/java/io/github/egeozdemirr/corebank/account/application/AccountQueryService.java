package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.BalanceReader;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService {

    private final AccountReader accountReader;
    private final BalanceReader balanceReader;

    public AccountQueryService(AccountReader accountReader, BalanceReader balanceReader) {
        this.accountReader = accountReader;
        this.balanceReader = balanceReader;
    }

    /** The balance comes from {@link BalanceReader}, so funding accounts show their ledger-derived balance too. */
    public AccountDetails getAccount(AccountId accountId) {
        Account account = accountReader.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return new AccountDetails(account, getBalance(accountId).balance());
    }

    public AccountBalance getBalance(AccountId accountId) {
        return balanceReader.findBalance(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
