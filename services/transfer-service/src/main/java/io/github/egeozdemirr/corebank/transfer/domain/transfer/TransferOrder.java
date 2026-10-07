package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.SameAccountTransferException;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.util.Objects;

/**
 * What the maker asked for. Immutable: approval and posting change the transfer's status, never its content.
 * Invariants: two different accounts, a positive amount, and one currency on both accounts and the amount.
 */
public record TransferOrder(
        AccountReference source,
        AccountReference target,
        BeneficiaryName beneficiaryName,
        Money amount,
        TransferChannel channel,
        UserId maker) {

    public TransferOrder {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(beneficiaryName, "beneficiaryName");
        Objects.requireNonNull(amount, "amount").requirePositive();
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(maker, "maker");
        if (source.accountId().equals(target.accountId())) {
            throw new SameAccountTransferException(source.accountId());
        }
        requireCurrencyOf(source, amount);
        requireCurrencyOf(target, amount);
    }

    private static void requireCurrencyOf(AccountReference account, Money amount) {
        if (!amount.hasCurrency(account.currency())) {
            throw new CurrencyMismatchException(account.currency(), amount.currency());
        }
    }
}
