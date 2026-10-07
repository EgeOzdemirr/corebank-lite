package io.github.egeozdemirr.corebank.transfer.api;

import io.github.egeozdemirr.corebank.transfer.domain.money.Currencies;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Amounts travel as decimal strings so that no client ever parses money as a binary floating point number. */
public record MonetaryAmountRequest(
        @NotNull
        @Pattern(regexp = "\\d{1,17}(\\.\\d{1,2})?", message = "must be a decimal with at most 2 fraction digits")
        @Schema(example = "1500.00")
        String amount,

        @NotNull
        @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code")
        @Schema(example = "TRY")
        String currency) {

    Money toMoney() {
        return Money.of(amount, Currencies.fromCode(currency));
    }
}
