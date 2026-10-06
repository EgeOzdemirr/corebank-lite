package io.github.egeozdemirr.corebank.account.domain.money;

import io.github.egeozdemirr.corebank.account.domain.exception.UnsupportedCurrencyException;
import java.util.Currency;
import java.util.Objects;

/** Turns an ISO 4217 code into a {@link Currency}, reporting unknown codes as a domain error. */
public final class Currencies {

    private Currencies() {
    }

    public static Currency fromCode(String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode");
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException unknownCode) {
            throw new UnsupportedCurrencyException(currencyCode, unknownCode);
        }
    }
}
