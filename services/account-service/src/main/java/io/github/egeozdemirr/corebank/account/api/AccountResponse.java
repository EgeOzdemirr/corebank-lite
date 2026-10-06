package io.github.egeozdemirr.corebank.account.api;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerOwner;
import io.github.egeozdemirr.corebank.account.domain.account.InstitutionOwner;
import java.time.Instant;
import java.util.UUID;

/** The holder's TCKN is never returned. {@code customerId} is null for the bank's own funding accounts. */
public record AccountResponse(
        UUID accountId,
        String iban,
        String accountType,
        String status,
        String holderName,
        UUID customerId,
        MoneyResponse balance,
        Instant openedAt) {

    static AccountResponse from(Account account) {
        UUID customerId = switch (account.owner()) {
            case CustomerOwner customer -> customer.customerId().value();
            case InstitutionOwner institution -> null;
        };
        return new AccountResponse(account.id().value(), account.iban().value(), account.type().name(),
                account.status().name(), account.owner().holderName().value(), customerId,
                MoneyResponse.from(account.balance()), account.openedAt());
    }
}
