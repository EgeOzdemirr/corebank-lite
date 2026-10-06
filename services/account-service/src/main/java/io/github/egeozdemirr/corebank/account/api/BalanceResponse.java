package io.github.egeozdemirr.corebank.account.api;

import io.github.egeozdemirr.corebank.account.application.AccountBalance;
import java.util.UUID;

public record BalanceResponse(UUID accountId, String iban, MoneyResponse balance) {

    static BalanceResponse from(AccountBalance balance) {
        return new BalanceResponse(balance.accountId().value(), balance.iban().value(),
                MoneyResponse.from(balance.balance()));
    }
}
