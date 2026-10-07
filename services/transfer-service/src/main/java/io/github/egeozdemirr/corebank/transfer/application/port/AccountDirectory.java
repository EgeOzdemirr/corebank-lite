package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.application.CustomerAccount;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;

/**
 * Looks up customer accounts in account-service. Unknown accounts and the bank's own accounts raise
 * {@code AccountNotFoundException}; a lookup without a usable answer raises {@code AccountServiceUnavailableException}.
 */
public interface AccountDirectory {

    CustomerAccount findById(AccountId accountId);

    CustomerAccount findByIban(Iban iban);
}
