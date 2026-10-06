package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.account.Account;

/** Stores accounts. {@link #update} must fail if another transaction changed the account first (optimistic lock). */
public interface AccountWriter {

    void add(Account account);

    void update(Account account);
}
