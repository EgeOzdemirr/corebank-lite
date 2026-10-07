package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.AccountOwner;
import io.github.egeozdemirr.corebank.account.domain.account.AccountStatus;
import io.github.egeozdemirr.corebank.account.domain.account.AccountType;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerOwner;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.account.InstitutionOwner;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.money.Currencies;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "account")
class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 26)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private AccountType accountType;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "holder_name", nullable = false, length = HolderName.MAX_LENGTH)
    private String holderName;

    @Column(name = "holder_tckn", length = 11)
    private String holderTckn;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Null for account types whose balance is derived from the ledger (ADR-0003). */
    @Column(precision = 19, scale = Money.SCALE)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    /** Optimistic lock: a concurrent balance update makes the second commit fail instead of losing an update. */
    @Version
    private Long version;

    protected AccountJpaEntity() {
        // required by JPA
    }

    static AccountJpaEntity newFrom(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.id = account.id().value();
        entity.iban = account.iban().value();
        entity.accountType = account.type();
        entity.holderName = account.owner().holderName().value();
        if (account.owner() instanceof CustomerOwner customer) {
            entity.customerId = customer.customerId().value();
            entity.holderTckn = customer.tckn().value();
        }
        entity.currency = account.currency().getCurrencyCode();
        entity.applyChangesFrom(account);
        entity.openedAt = account.openedAt();
        return entity;
    }

    /** Copies the only mutable parts of the aggregate. */
    void applyChangesFrom(Account account) {
        this.balance = account.materialisedBalance().map(Money::amount).orElse(null);
        this.status = account.status();
    }

    Account toDomain() {
        AccountId accountId = new AccountId(id);
        Currency accountCurrency = Currencies.fromCode(currency);
        if (!accountType.materialisesBalance()) {
            return Account.restoreWithLedgerBalance(accountId, new Iban(iban), toOwner(), accountCurrency, openedAt,
                    status);
        }
        return Account.restore(accountId, new Iban(iban), toOwner(), openedAt, status,
                Money.of(balance, accountCurrency));
    }

    private AccountOwner toOwner() {
        HolderName name = new HolderName(holderName);
        return switch (accountType) {
            case CUSTOMER -> new CustomerOwner(new CustomerId(customerId), name, new Tckn(holderTckn));
            case FUNDING -> new InstitutionOwner(name);
        };
    }

    UUID id() {
        return id;
    }

    AccountType accountType() {
        return accountType;
    }
}
