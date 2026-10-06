package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.application.AccountBalance;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import java.util.Optional;

/** Read-only, hot-path balance lookup; kept apart from {@link AccountReader} so it can bypass the ORM. */
public interface BalanceReader {

    Optional<AccountBalance> findBalance(AccountId accountId);
}
