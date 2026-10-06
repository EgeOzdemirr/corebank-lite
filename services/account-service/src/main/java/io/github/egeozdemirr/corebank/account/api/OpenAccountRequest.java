package io.github.egeozdemirr.corebank.account.api;

import io.github.egeozdemirr.corebank.account.application.OpenAccountCommand;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.money.Currencies;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Currency;
import java.util.UUID;

/** Amounts travel as decimal strings so that no client ever parses money as a binary floating point number. */
public record OpenAccountRequest(
        @NotNull
        UUID customerId,

        @NotBlank
        @Size(max = HolderName.MAX_LENGTH)
        String holderName,

        @NotNull
        @Pattern(regexp = "\\d{11}", message = "must be 11 digits")
        @Schema(description = "Synthetic T.C. Kimlik No; must satisfy the check digit algorithm",
                example = "10000000146")
        String holderTckn,

        @NotNull
        @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code")
        @Schema(example = "TRY")
        String currency,

        @Pattern(regexp = "\\d{1,17}(\\.\\d{1,2})?", message = "must be a decimal with at most 2 fraction digits")
        @Schema(description = "Optional; posted from the bank's funding account", example = "1500.00")
        String openingDeposit) {

    OpenAccountCommand toCommand() {
        Currency accountCurrency = Currencies.fromCode(currency);
        Money deposit = openingDeposit == null
                ? Money.zero(accountCurrency)
                : Money.of(openingDeposit, accountCurrency);
        return new OpenAccountCommand(new CustomerId(customerId), new HolderName(holderName), new Tckn(holderTckn),
                accountCurrency, deposit);
    }
}
