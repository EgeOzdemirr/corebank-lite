package io.github.egeozdemirr.corebank.account.domain.account;

import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotActiveException;
import io.github.egeozdemirr.corebank.account.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidPostingException;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

/**
 * Account aggregate. Whether it carries a balance depends on its type (see {@link AccountType}): a materialised
 * balance changes only by posting a ledger entry and is guarded by locking in the persistence layer; a derived
 * balance is not part of the aggregate at all and is read from the ledger through {@code BalanceReader}.
 */
public final class Account {

    private final AccountId id;
    private final Iban iban;
    private final AccountOwner owner;
    private final Currency currency;
    private final Instant openedAt;
    private final AccountStatus status;
    /** Null exactly when the type derives the balance from the ledger. */
    private Money balance;

    private Account(AccountId id, Iban iban, AccountOwner owner, Currency currency, Instant openedAt,
                    AccountStatus status, Money balance) {
        this.id = Objects.requireNonNull(id, "id");
        this.iban = Objects.requireNonNull(iban, "iban");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.balance = balance;
        requireBalanceMatchesType();
    }

    public static Account open(AccountId id, Iban iban, AccountOwner owner, Currency currency, Instant openedAt) {
        Money openingBalance = owner.accountType().materialisesBalance() ? Money.zero(currency) : null;
        return new Account(id, iban, owner, currency, openedAt, AccountStatus.ACTIVE, openingBalance);
    }

    /** Rebuilds an account whose type materialises its balance; no business rule is applied. */
    public static Account restore(AccountId id, Iban iban, AccountOwner owner, Instant openedAt,
                                  AccountStatus status, Money balance) {
        Objects.requireNonNull(balance, "balance");
        return new Account(id, iban, owner, balance.currency(), openedAt, status, balance);
    }

    /** Rebuilds an account whose type derives its balance from the ledger; no business rule is applied. */
    public static Account restoreWithLedgerBalance(AccountId id, Iban iban, AccountOwner owner, Currency currency,
                                                   Instant openedAt, AccountStatus status) {
        return new Account(id, iban, owner, currency, openedAt, status, null);
    }

    /**
     * Applies one ledger line. A materialised balance moves (credits increase, debits decrease) and must stay
     * non-negative unless the type allows otherwise; a derived balance has nothing to update here.
     */
    public void post(LedgerEntry entry) {
        requireOwnEntry(entry);
        requireActive();
        requireOwnCurrency(entry);
        if (!type().materialisesBalance()) {
            return;
        }
        Money newBalance = balance.plus(entry.signedAmount());
        if (newBalance.isNegative() && !type().allowsNegativeBalance()) {
            throw new InsufficientFundsException(id);
        }
        balance = newBalance;
    }

    private void requireBalanceMatchesType() {
        if (type().materialisesBalance() != (balance != null)) {
            throw new IllegalArgumentException("Account " + id + " of type " + type()
                    + (type().materialisesBalance() ? " needs" : " must not carry") + " a materialised balance");
        }
        if (balance != null && !balance.hasCurrency(currency)) {
            throw new CurrencyMismatchException(currency, balance.currency());
        }
    }

    private void requireOwnEntry(LedgerEntry entry) {
        if (!entry.accountId().equals(id)) {
            throw new InvalidPostingException("Ledger entry " + entry.id() + " does not belong to account " + id);
        }
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id);
        }
    }

    private void requireOwnCurrency(LedgerEntry entry) {
        if (!entry.amount().hasCurrency(currency)) {
            throw new CurrencyMismatchException(currency, entry.amount().currency());
        }
    }

    public AccountId id() {
        return id;
    }

    public Iban iban() {
        return iban;
    }

    public AccountOwner owner() {
        return owner;
    }

    public AccountType type() {
        return owner.accountType();
    }

    public Currency currency() {
        return currency;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public AccountStatus status() {
        return status;
    }

    /**
     * The materialised balance. Accounts whose balance is derived from the ledger have none; asking for it is a
     * programming error, their balance comes from {@code BalanceReader}.
     */
    public Money balance() {
        if (!type().materialisesBalance()) {
            throw new IllegalStateException("Account " + id + " derives its balance from the ledger");
        }
        return balance;
    }

    /** The materialised balance, or empty if the type derives the balance from the ledger. */
    public Optional<Money> materialisedBalance() {
        return Optional.ofNullable(balance);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Account[id=" + id + ", iban=" + iban + ", type=" + type() + ", status=" + status + "]";
    }
}
