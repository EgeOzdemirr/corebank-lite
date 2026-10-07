package io.github.egeozdemirr.corebank.transfer.api;

import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;

public record MoneyResponse(
        @Schema(example = "1500.00") String amount,
        @Schema(example = "TRY") String currency) {

    static MoneyResponse from(Money money) {
        return new MoneyResponse(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
}
