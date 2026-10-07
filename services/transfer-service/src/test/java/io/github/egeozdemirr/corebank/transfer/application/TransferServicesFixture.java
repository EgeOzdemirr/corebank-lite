package io.github.egeozdemirr.corebank.transfer.application;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TARGET_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TRY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.account;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.BeneficiaryName;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferChannel;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import io.github.egeozdemirr.corebank.transfer.support.FakeAccountDirectory;
import io.github.egeozdemirr.corebank.transfer.support.InMemoryTransfers;
import io.github.egeozdemirr.corebank.transfer.support.MutableClock;
import io.github.egeozdemirr.corebank.transfer.support.ScriptedPostingGateway;
import io.github.egeozdemirr.corebank.transfer.support.TestTransfers;

/** The application services wired to in-memory fakes, with one source and one target account. */
final class TransferServicesFixture {

    final InMemoryTransfers store = new InMemoryTransfers();
    final FakeAccountDirectory accounts = new FakeAccountDirectory();
    final ScriptedPostingGateway ledger = new ScriptedPostingGateway();
    final MutableClock clock = new MutableClock(NOW);
    final AccountReference source = account(SOURCE_IBAN, TRY);
    final AccountReference target = account(TARGET_IBAN, TRY);

    private final TransferRecorder recorder = new TransferRecorder(store, store, store, store, store, ISTANBUL);
    private final TransferPostingStep postingStep = new TransferPostingStep(ledger, recorder, clock);

    final CreateTransferService createService =
            new CreateTransferService(accounts, POLICY, recorder, postingStep, clock);
    final ApproveTransferService approveService =
            new ApproveTransferService(recorder, postingStep, ISTANBUL, clock);
    final RejectTransferService rejectService = new RejectTransferService(recorder, ISTANBUL, clock);
    final TransferQueryService queryService = new TransferQueryService(store);

    TransferServicesFixture() {
        accounts.add(source, true);
        accounts.add(target, true);
    }

    CreateTransferCommand command(String amount) {
        return command(Money.of(amount, TRY), TestTransfers.MAKER);
    }

    CreateTransferCommand command(Money amount, UserId maker) {
        return new CreateTransferCommand(source.accountId(), target.iban(), new BeneficiaryName("Mehmet Demir"),
                amount, TransferChannel.MOBILE, maker);
    }

    Money usedToday() {
        return store.dailyTotal(source.accountId(), ISTANBUL.businessDayOf(NOW), Money.zero(TRY));
    }
}
