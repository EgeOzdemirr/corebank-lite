package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import java.util.Optional;

/**
 * Loads an account that a posting is about to change, in the caller's transaction.
 *
 * <p>An account that materialises its balance is locked ({@code SELECT ... FOR UPDATE}) and read after the lock is
 * granted, so its balance is current and no other posting can change it before commit. An account whose balance is
 * derived from the ledger is never written by a posting and is read without a lock, which keeps the funding
 * accounts free of contention (ADR-0003). Callers lock the accounts of one posting in {@code AccountId} order, so
 * two postings over the same accounts cannot deadlock.
 */
public interface AccountLocker {

    Optional<Account> lockForPosting(AccountId accountId);
}
