package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import java.util.Currency;
import java.util.Optional;

/** Loads accounts. Implementations return fresh aggregates whose later changes are saved with {@link AccountWriter}. */
public interface AccountReader {

    Optional<Account> findById(AccountId accountId);

    /** The bank's funding account for a currency; a currency without one is not supported. */
    Optional<Account> findFundingAccount(Currency currency);
}
