package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
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

    private final AccountReader accountReader;
    private final AccountWriter accountWriter;
    private final LedgerWriter ledgerWriter;

    public LedgerPostingService(AccountReader accountReader, AccountWriter accountWriter, LedgerWriter ledgerWriter) {
        this.accountReader = accountReader;
        this.accountWriter = accountWriter;
        this.ledgerWriter = ledgerWriter;
    }

    /**
     * Accounts are loaded and updated in id order so that two postings touching the same accounts always take row
     * locks in the same order, which rules out deadlocks between them.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(Posting posting) {
        List<Account> accounts = posting.accountIdsInLockOrder().stream().map(this::load).toList();
        Map<AccountId, Account> accountsById = accounts.stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));

        for (LedgerEntry entry : posting.entries()) {
            accountsById.get(entry.accountId()).post(entry);
        }
        accounts.forEach(accountWriter::update);
        ledgerWriter.append(posting);
    }

    private Account load(AccountId accountId) {
        return accountReader.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
