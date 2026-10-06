package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.LedgerReader;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LedgerQueryService {

    private final AccountReader accountReader;
    private final LedgerReader ledgerReader;

    public LedgerQueryService(AccountReader accountReader, LedgerReader ledgerReader) {
        this.accountReader = accountReader;
        this.ledgerReader = ledgerReader;
    }

    /** An unknown account is reported as not found rather than as an empty page. */
    public PageResult<LedgerEntry> getEntries(AccountId accountId, PageQuery pageQuery) {
        if (accountReader.findById(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return ledgerReader.findEntries(accountId, pageQuery);
    }
}
