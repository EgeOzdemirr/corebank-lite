package io.github.egeozdemirr.corebank.account.domain.account;

import java.util.Objects;
import java.util.UUID;

/** Identity of an account. */
// Records generate equals() and hashCode(); PMD does not see them.
@SuppressWarnings("PMD.OverrideBothEqualsAndHashCodeOnComparable")
public record AccountId(UUID value) implements Comparable<AccountId> {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }

    /** Natural order is the lock order: accounts touched by one posting are always loaded in this order. */
    @Override
    public int compareTo(AccountId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
