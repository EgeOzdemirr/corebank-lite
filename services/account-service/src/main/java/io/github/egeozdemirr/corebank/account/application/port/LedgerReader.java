package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.application.PageQuery;
import io.github.egeozdemirr.corebank.account.application.PageResult;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;

public interface LedgerReader {

    /** Entries of one account, newest first. */
    PageResult<LedgerEntry> findEntries(AccountId accountId, PageQuery pageQuery);
}
