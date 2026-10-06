package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.Currency;
import java.util.Objects;

/** Request to open a customer account. A zero opening deposit means the account starts empty. */
public record OpenAccountCommand(
        CustomerId customerId,
        HolderName holderName,
        Tckn tckn,
        Currency currency,
        Money openingDeposit) {

    public OpenAccountCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(tckn, "tckn");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(openingDeposit, "openingDeposit").requireNotNegative();
        if (!openingDeposit.hasCurrency(currency)) {
            throw new CurrencyMismatchException(currency, openingDeposit.currency());
        }
    }
}
