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

    public Account getAccount(AccountId accountId) {
        return accountReader.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public AccountBalance getBalance(AccountId accountId) {
        return balanceReader.findBalance(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
