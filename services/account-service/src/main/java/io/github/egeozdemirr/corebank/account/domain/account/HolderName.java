package io.github.egeozdemirr.corebank.account.domain.account;

import io.github.egeozdemirr.corebank.account.domain.exception.InvalidHolderNameException;
import java.util.Objects;

/** Name of the account holder as it appears on the account; screened against sanctions lists downstream. */
public record HolderName(String value) {

    public static final int MAX_LENGTH = 140;

    public HolderName {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) {
            throw new InvalidHolderNameException("Holder name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidHolderNameException("Holder name must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
