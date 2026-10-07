package io.github.egeozdemirr.corebank.account.application;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.TRY;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotEligibleForPostingException;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidAmountException;
import io.github.egeozdemirr.corebank.account.domain.exception.PostingConflictException;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.support.InMemoryAccountStore;
import io.github.egeozdemirr.corebank.account.support.InMemoryLedger;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T09:30:00Z");

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final InMemoryAccountStore accounts = new InMemoryAccountStore(ledger);
    private final MutableClock clock = new MutableClock(NOW);
    private final PostTransferService service = new PostTransferService(
            new LedgerPostingService(accounts, accounts, ledger), ledger, clock);
    private Account payer;
    private Account payee;

    @BeforeEach
    void setUp() {
        payer = TestAccounts.customerAccount("100.00");
        payee = TestAccounts.customerAccount("0.00");
        accounts.add(payer);
        accounts.add(payee);
    }

    @Test
    void post_movesMoneyAsOneTransferPosting() {
        PostingId postingId = PostingId.newId();

        PostTransferResult result = service.post(command(postingId, "40.00"));

        assertThat(result).isEqualTo(new PostTransferResult(postingId, NOW, false));
        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("60.00"));
        assertThat(accounts.stored(payee.id()).balance()).isEqualTo(money("40.00"));
        assertThat(ledger.postings()).singleElement()
                .satisfies(posting -> assertThat(posting.type()).isEqualTo(PostingType.TRANSFER));
    }

    @Test
    void repeatedRequest_returnsOriginalResultAndMovesNoMoney() {
        PostingId postingId = PostingId.newId();
        service.post(command(postingId, "40.00"));
        clock.advanceSeconds(30);

        PostTransferResult repeated = service.post(command(postingId, "40.00"));

        assertThat(repeated).isEqualTo(new PostTransferResult(postingId, NOW, true));
        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("60.00"));
        assertThat(ledger.postings()).hasSize(1);
    }

    @Test
    void reusedIdForAnotherAmount_isConflictAndChangesNothing() {
        PostingId postingId = PostingId.newId();
        service.post(command(postingId, "40.00"));

        assertThatThrownBy(() -> service.post(command(postingId, "41.00")))
                .isInstanceOf(PostingConflictException.class);
        assertThat(accounts.stored(payer.id()).balance()).isEqualTo(money("60.00"));
    }

    @Test
    void reusedIdForReversedDirection_isConflict() {
        PostingId postingId = PostingId.newId();
        service.post(command(postingId, "40.00"));

        assertThatThrownBy(() -> service.post(new PostTransferCommand(postingId, payee.id(), payer.id(),
                money("40.00")))).isInstanceOf(PostingConflictException.class);
    }

    @Test
    void transferFromFundingAccount_isNotEligible() {
        Account funding = TestAccounts.fundingAccount(TRY);
        accounts.add(funding);

        assertThatThrownBy(() -> service.post(new PostTransferCommand(PostingId.newId(), funding.id(), payee.id(),
                money("1.00")))).isInstanceOf(AccountNotEligibleForPostingException.class);
    }

    @Test
    void businessRejections_surfaceAsDomainExceptions() {
        assertThatThrownBy(() -> service.post(command(PostingId.newId(), "100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> service.post(new PostTransferCommand(PostingId.newId(), payer.id(),
                AccountId.newId(), money("1.00")))).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void command_requiresPositiveAmount() {
        assertThatThrownBy(() -> command(PostingId.newId(), "0.00")).isInstanceOf(InvalidAmountException.class);
    }

    private PostTransferCommand command(PostingId postingId, String amount) {
        return new PostTransferCommand(postingId, payer.id(), payee.id(), money(amount));
    }

    /** Lets a test show that a repeated request reports the original posting time, not the time of the retry. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
