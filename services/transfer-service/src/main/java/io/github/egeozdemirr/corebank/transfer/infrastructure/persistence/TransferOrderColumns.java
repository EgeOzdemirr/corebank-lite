package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.money.Currencies;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.BeneficiaryName;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferChannel;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/** The immutable order of a transfer; written once, never updated. */
@Embeddable
class TransferOrderColumns {

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "source_iban", nullable = false, updatable = false, length = 26)
    private String sourceIban;

    @Column(name = "target_account_id", nullable = false, updatable = false)
    private UUID targetAccountId;

    @Column(name = "target_iban", nullable = false, updatable = false, length = 26)
    private String targetIban;

    @Column(name = "beneficiary_name", nullable = false, updatable = false, length = BeneficiaryName.MAX_LENGTH)
    private String beneficiaryName;

    @Column(nullable = false, updatable = false, precision = 19, scale = Money.SCALE)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private TransferChannel channel;

    @Column(name = "maker_user_id", nullable = false, updatable = false, length = 64)
    private String makerUserId;

    protected TransferOrderColumns() {
        // required by JPA
    }

    static TransferOrderColumns from(TransferOrder order) {
        TransferOrderColumns columns = new TransferOrderColumns();
        columns.sourceAccountId = order.source().accountId().value();
        columns.sourceIban = order.source().iban().value();
        columns.targetAccountId = order.target().accountId().value();
        columns.targetIban = order.target().iban().value();
        columns.beneficiaryName = order.beneficiaryName().value();
        columns.amount = order.amount().amount();
        columns.currency = order.amount().currency().getCurrencyCode();
        columns.channel = order.channel();
        columns.makerUserId = order.maker().value();
        return columns;
    }

    /** Both accounts carry the transfer's currency: TransferOrder refuses anything else. */
    TransferOrder toDomain() {
        Currency transferCurrency = Currencies.fromCode(currency);
        return new TransferOrder(
                new AccountReference(new AccountId(sourceAccountId), new Iban(sourceIban), transferCurrency),
                new AccountReference(new AccountId(targetAccountId), new Iban(targetIban), transferCurrency),
                new BeneficiaryName(beneficiaryName), Money.of(amount, transferCurrency), channel,
                new UserId(makerUserId));
    }
}
