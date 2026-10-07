package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.application.port.AccountLocker;
import io.github.egeozdemirr.corebank.account.application.port.AccountWriter;
import io.github.egeozdemirr.corebank.account.application.port.LedgerWriter;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Applies a balanced posting to account balances and appends it to the ledger in the caller's transaction. */
@Service
public class LedgerPostingService {

    private final AccountLocker accountLocker;
    private final AccountWriter accountWriter;
    private final LedgerWriter ledgerWriter;

    public LedgerPostingService(AccountLocker accountLocker, AccountWriter accountWriter, LedgerWriter ledgerWriter) {
        this.accountLocker = accountLocker;
        this.accountWriter = accountWriter;
        this.ledgerWriter = ledgerWriter;
    }

    /**
     * Accounts are locked in id order so that two postings touching the same accounts always take row locks in the
     * same order, which rules out deadlocks between them. Only accounts with a materialised balance are written;
     * funding accounts are validated but never locked or updated (ADR-0003).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(Posting posting) {
        List<Account> accounts = posting.accountIdsInLockOrder().stream().map(this::lock).toList();
        Map<AccountId, Account> accountsById = accounts.stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));

        for (LedgerEntry entry : posting.entries()) {
            accountsById.get(entry.accountId()).post(entry);
        }
        accounts.stream().filter(account -> account.type().materialisesBalance()).forEach(accountWriter::update);
        ledgerWriter.append(posting);
    }

    private Account lock(AccountId accountId) {
        return accountLocker.lockForPosting(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
