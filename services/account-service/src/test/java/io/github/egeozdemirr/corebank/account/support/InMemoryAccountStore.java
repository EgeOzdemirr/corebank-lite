package io.github.egeozdemirr.corebank.account.support;

import io.github.egeozdemirr.corebank.account.application.AccountBalance;
import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.AccountWriter;
import io.github.egeozdemirr.corebank.account.application.port.BalanceReader;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.AccountType;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake of the account ports. Like the real store it hands out copies, so a test only sees changes that were
 * explicitly written back with {@link #update}.
 */
public final class InMemoryAccountStore implements AccountReader, AccountWriter, BalanceReader {

    private final Map<AccountId, Account> accounts = new LinkedHashMap<>();
    private final List<AccountId> updateOrder = new ArrayList<>();

    @Override
    public Optional<Account> findById(AccountId accountId) {
        return Optional.ofNullable(accounts.get(accountId)).map(InMemoryAccountStore::copy);
    }

    @Override
    public Optional<Account> findFundingAccount(Currency currency) {
        return accounts.values().stream()
                .filter(account -> account.type() == AccountType.FUNDING && account.currency().equals(currency))
                .findFirst()
                .map(InMemoryAccountStore::copy);
    }

    @Override
    public void add(Account account) {
        accounts.put(account.id(), copy(account));
    }

    @Override
    public void update(Account account) {
        accounts.put(account.id(), copy(account));
        updateOrder.add(account.id());
    }

    @Override
    public Optional<AccountBalance> findBalance(AccountId accountId) {
        return Optional.ofNullable(accounts.get(accountId))
                .map(account -> new AccountBalance(account.id(), account.iban(), account.balance()));
    }

    public Account stored(AccountId accountId) {
        return copy(accounts.get(accountId));
    }

    public List<AccountId> updateOrder() {
        return List.copyOf(updateOrder);
    }

    private static Account copy(Account account) {
        return Account.restore(account.id(), account.iban(), account.owner(), account.openedAt(), account.status(),
                account.balance());
    }
}
