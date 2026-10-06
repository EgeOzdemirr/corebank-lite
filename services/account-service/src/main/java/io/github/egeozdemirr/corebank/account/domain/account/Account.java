package io.github.egeozdemirr.corebank.account.domain.account;

import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotActiveException;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidPostingException;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Account aggregate. The balance is a materialised view of the ledger: it changes only by posting a ledger entry,
 * and the persistence layer guards it with optimistic locking.
 */
public final class Account {

    private final AccountId id;
    private final Iban iban;
    private final AccountOwner owner;
    private final Instant openedAt;
    private final AccountStatus status;
    private Money balance;

    private Account(AccountId id, Iban iban, AccountOwner owner, Instant openedAt, AccountStatus status,
                    Money balance) {
        this.id = Objects.requireNonNull(id, "id");
        this.iban = Objects.requireNonNull(iban, "iban");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.balance = Objects.requireNonNull(balance, "balance");
    }

    public static Account open(AccountId id, Iban iban, AccountOwner owner, Currency currency, Instant openedAt) {
        return new Account(id, iban, owner, openedAt, AccountStatus.ACTIVE, Money.zero(currency));
    }

    /** Rebuilds an account from storage; no business rule is applied. */
    public static Account restore(AccountId id, Iban iban, AccountOwner owner, Instant openedAt,
                                  AccountStatus status, Money balance) {
        return new Account(id, iban, owner, openedAt, status, balance);
    }

    /** Applies one ledger line to the balance. Credits increase and debits decrease the balance. */
    public void post(LedgerEntry entry) {
        requireOwnEntry(entry);
        requireActive();
        Money newBalance = balance.plus(entry.signedAmount());
        if (newBalance.isNegative() && !type().allowsNegativeBalance()) {
            throw new InsufficientFundsException(id);
        }
        balance = newBalance;
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
        return balance.currency();
    }

    public Instant openedAt() {
        return openedAt;
    }

    public AccountStatus status() {
        return status;
    }

    public Money balance() {
        return balance;
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
