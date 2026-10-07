package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.BeneficiaryName;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferChannel;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.util.Objects;

/** A maker asks to move {@code amount} from a customer account to the customer account with {@code targetIban}. */
public record CreateTransferCommand(
        AccountId sourceAccountId,
        Iban targetIban,
        BeneficiaryName beneficiaryName,
        Money amount,
        TransferChannel channel,
        UserId maker) {

    public CreateTransferCommand {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(targetIban, "targetIban");
        Objects.requireNonNull(beneficiaryName, "beneficiaryName");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(maker, "maker");
    }
}
