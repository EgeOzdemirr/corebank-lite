package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.AccountWriter;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.AccountType;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import java.util.Currency;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaAccountStore implements AccountReader, AccountWriter {

    private final AccountJpaRepository repository;

    JpaAccountStore(AccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
        return repository.findById(accountId.value()).map(AccountJpaEntity::toDomain);
    }

    @Override
    public Optional<Account> findFundingAccount(Currency currency) {
        return repository.findByAccountTypeAndCurrency(AccountType.FUNDING, currency.getCurrencyCode())
                .map(AccountJpaEntity::toDomain);
    }

    @Override
    public void add(Account account) {
        repository.save(AccountJpaEntity.newFrom(account));
    }

    /**
     * Changes the managed entity loaded earlier in this transaction; Hibernate then issues
     * {@code UPDATE ... WHERE version = ?} at flush and fails if another transaction got there first.
     */
    @Override
    public void update(Account account) {
        AccountJpaEntity entity = repository.findById(account.id().value())
                .orElseThrow(() -> new AccountNotFoundException(account.id()));
        entity.applyChangesFrom(account);
    }
}
