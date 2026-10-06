package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.application.OpenAccountCommand;
import io.github.egeozdemirr.corebank.account.application.OpenAccountService;
import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.AccountWriter;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.ledger.EntryDirection;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntryId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Two transactions that read the same balance cannot both write it: the later commit fails. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class OptimisticLockingIT {

    @Autowired
    private OpenAccountService openAccountService;

    @Autowired
    private AccountReader accountReader;

    @Autowired
    private AccountWriter accountWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentBalanceUpdate_isDetectedInsteadOfLost() {
        AccountId accountId = openAccountWith("100.00");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Account staleCopy = accountReader.findById(accountId).orElseThrow();
            staleCopy.post(credit(accountId, "1.00"));

            concurrent.executeWithoutResult(inner -> {
                Account freshCopy = accountReader.findById(accountId).orElseThrow();
                freshCopy.post(credit(accountId, "5.00"));
                accountWriter.update(freshCopy);
            });

            accountWriter.update(staleCopy);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        Account stored = accountReader.findById(accountId).orElseThrow();
        assertThat(stored.balance()).isEqualTo(TestAccounts.money("105.00"));
    }

    private AccountId openAccountWith(String deposit) {
        return openAccountService.open(new OpenAccountCommand(CustomerId.newId(),
                new HolderName(SyntheticData.fullName()), new Tckn(SyntheticData.tckn()), TestAccounts.TRY,
                TestAccounts.money(deposit))).id();
    }

    private static LedgerEntry credit(AccountId accountId, String amount) {
        return new LedgerEntry(LedgerEntryId.newId(), PostingId.newId(), accountId, EntryDirection.CREDIT,
                Money.of(amount, TestAccounts.TRY), PostingType.OPENING_DEPOSIT, Instant.now());
    }
}
