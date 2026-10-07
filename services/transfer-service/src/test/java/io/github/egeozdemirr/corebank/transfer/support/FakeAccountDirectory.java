package io.github.egeozdemirr.corebank.transfer.support;

import io.github.egeozdemirr.corebank.transfer.application.AccountServiceUnavailableException;
import io.github.egeozdemirr.corebank.transfer.application.CustomerAccount;
import io.github.egeozdemirr.corebank.transfer.application.port.AccountDirectory;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Customer accounts known to the test; counts lookups so tests can show that local checks come first. */
public final class FakeAccountDirectory implements AccountDirectory {

    private final List<CustomerAccount> accounts = new ArrayList<>();
    private boolean unavailable;
    private int lookups;

    public void add(AccountReference account, boolean active) {
        accounts.add(new CustomerAccount(account, active));
    }

    public void makeUnavailable() {
        unavailable = true;
    }

    public int lookups() {
        return lookups;
    }

    @Override
    public CustomerAccount findById(AccountId accountId) {
        return find(accounts.stream().filter(account -> account.reference().accountId().equals(accountId))
                .findFirst()).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Override
    public CustomerAccount findByIban(Iban iban) {
        return find(accounts.stream().filter(account -> account.reference().iban().equals(iban))
                .findFirst()).orElseThrow(() -> new AccountNotFoundException(iban));
    }

    private Optional<CustomerAccount> find(Optional<CustomerAccount> account) {
        lookups++;
        if (unavailable) {
            throw new AccountServiceUnavailableException("account-service is down in this test", null);
        }
        return account;
    }
}
