package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.application.port.AccountEventPublisher;
import io.github.egeozdemirr.corebank.account.application.port.AccountReader;
import io.github.egeozdemirr.corebank.account.application.port.AccountWriter;
import io.github.egeozdemirr.corebank.account.application.port.IbanGenerator;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerOwner;
import io.github.egeozdemirr.corebank.account.domain.event.AccountOpened;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.exception.UnsupportedCurrencyException;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenAccountService {

    private final AccountReader accountReader;
    private final AccountWriter accountWriter;
    private final IbanGenerator ibanGenerator;
    private final AccountEventPublisher eventPublisher;
    private final LedgerPostingService ledgerPostingService;
    private final Clock clock;

    public OpenAccountService(AccountReader accountReader, AccountWriter accountWriter, IbanGenerator ibanGenerator,
                              AccountEventPublisher eventPublisher, LedgerPostingService ledgerPostingService,
                              Clock clock) {
        this.accountReader = accountReader;
        this.accountWriter = accountWriter;
        this.ibanGenerator = ibanGenerator;
        this.eventPublisher = eventPublisher;
        this.ledgerPostingService = ledgerPostingService;
        this.clock = clock;
    }

    /**
     * Opens the account, records AccountOpened in the outbox and, if there is an opening deposit, posts it from the
     * currency's funding account. All of it commits or rolls back together.
     */
    @Transactional
    public Account open(OpenAccountCommand command) {
        Account fundingAccount = accountReader.findFundingAccount(command.currency())
                .orElseThrow(() -> new UnsupportedCurrencyException(command.currency().getCurrencyCode()));
        CustomerOwner owner = new CustomerOwner(command.customerId(), command.holderName(), command.tckn());
        Instant now = clock.instant();

        Account account = Account.open(AccountId.newId(), ibanGenerator.nextIban(), owner, command.currency(), now);
        accountWriter.add(account);
        eventPublisher.publish(AccountOpened.of(account, owner));

        if (command.openingDeposit().isPositive()) {
            ledgerPostingService.post(Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT,
                    fundingAccount.id(), account.id(), command.openingDeposit(), now));
        }
        return accountReader.findById(account.id())
                .orElseThrow(() -> new AccountNotFoundException(account.id()));
    }
}
