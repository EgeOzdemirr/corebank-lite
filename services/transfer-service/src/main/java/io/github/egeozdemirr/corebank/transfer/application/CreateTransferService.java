package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.application.port.AccountDirectory;
import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotActiveException;
import io.github.egeozdemirr.corebank.transfer.domain.policy.TransferPolicy;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Creates a transfer. Every check that can fail runs before anything is recorded, so a rejected request leaves no
 * row, no event and no limit usage behind; then the transfer is recorded and, if approved, posted.
 */
@Service
public class CreateTransferService {

    private final AccountDirectory accountDirectory;
    private final TransferPolicy policy;
    private final TransferRecorder recorder;
    private final TransferPostingStep postingStep;
    private final Clock clock;

    public CreateTransferService(AccountDirectory accountDirectory, TransferPolicy policy, TransferRecorder recorder,
                                 TransferPostingStep postingStep, Clock clock) {
        this.accountDirectory = accountDirectory;
        this.policy = policy;
        this.recorder = recorder;
        this.postingStep = postingStep;
        this.clock = clock;
    }

    /** Local checks first (currency, single limit), so an invalid request never waits for account-service. */
    public Transfer create(CreateTransferCommand command) {
        policy.requireWithinSingleTransactionLimit(command.amount());
        CustomerAccount source = requireActive(accountDirectory.findById(command.sourceAccountId()));
        CustomerAccount target = requireActive(accountDirectory.findByIban(command.targetIban()));
        TransferOrder order = new TransferOrder(source.reference(), target.reference(), command.beneficiaryName(),
                command.amount(), command.channel(), command.maker());
        Transfer transfer = Transfer.request(TransferId.newId(), order, policy, clock.instant());
        Transfer recorded = recorder.recordRequest(transfer, policy.dailyLimit(command.amount().currency()));
        return postingStep.postIfApproved(recorded);
    }

    private static CustomerAccount requireActive(CustomerAccount account) {
        if (!account.active()) {
            throw new AccountNotActiveException(account.reference().accountId());
        }
        return account;
    }
}
