package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.application.port.AccountLocker;
import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.AccountWriter;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.AccountType;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Currency;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaAccountStore implements AccountReader, AccountWriter, AccountLocker {

    private final AccountJpaRepository repository;
    private final EntityManager entityManager;

    JpaAccountStore(AccountJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
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

    /** Flushed at once so that a posting later in the same transaction can lock the new row. */
    @Override
    public void add(Account account) {
        repository.saveAndFlush(AccountJpaEntity.newFrom(account));
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

    /**
     * The refresh re-reads the row with {@code SELECT ... FOR UPDATE}, so the balance is the one committed before
     * the lock was granted even if the entity was already loaded in this transaction.
     */
    @Override
    public Optional<Account> lockForPosting(AccountId accountId) {
        Optional<AccountJpaEntity> entity = repository.findById(accountId.value());
        entity.filter(candidate -> candidate.accountType().materialisesBalance())
                .ifPresent(candidate -> entityManager.refresh(candidate, LockModeType.PESSIMISTIC_WRITE));
        return entity.map(AccountJpaEntity::toDomain);
    }
}
