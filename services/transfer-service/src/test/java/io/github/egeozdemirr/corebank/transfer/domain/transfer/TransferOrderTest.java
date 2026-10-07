package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.MAKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TARGET_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TRY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.USD;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.account;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidAmountException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidBeneficiaryNameException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.SameAccountTransferException;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import org.junit.jupiter.api.Test;

class TransferOrderTest {

    private static final BeneficiaryName BENEFICIARY = new BeneficiaryName("Mehmet Demir");

    @Test
    void transferToTheSameAccount_isRejected() {
        AccountReference source = account(SOURCE_IBAN, TRY);

        assertThatThrownBy(() -> new TransferOrder(source, source, BENEFICIARY, money("1.00"),
                TransferChannel.BRANCH, MAKER)).isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void amountInAnotherCurrencyThanEitherAccount_isRejected() {
        AccountReference lira = account(SOURCE_IBAN, TRY);
        AccountReference dollar = account(TARGET_IBAN, USD);

        assertThatThrownBy(() -> new TransferOrder(lira, dollar, BENEFICIARY, money("1.00"),
                TransferChannel.BRANCH, MAKER)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> new TransferOrder(dollar, lira, BENEFICIARY, money("1.00"),
                TransferChannel.BRANCH, MAKER)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void amount_mustBePositive() {
        assertThatThrownBy(() -> new TransferOrder(account(SOURCE_IBAN, TRY), account(TARGET_IBAN, TRY),
                BENEFICIARY, Money.zero(TRY), TransferChannel.BRANCH, MAKER))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void beneficiaryName_isTrimmedAndBounded() {
        assertThat(new BeneficiaryName("  Ayşe Yılmaz ").value()).isEqualTo("Ayşe Yılmaz");
        assertThat(new BeneficiaryName("x".repeat(BeneficiaryName.MAX_LENGTH)).value())
                .hasSize(BeneficiaryName.MAX_LENGTH);
        assertThatThrownBy(() -> new BeneficiaryName("   ")).isInstanceOf(InvalidBeneficiaryNameException.class);
        assertThatThrownBy(() -> new BeneficiaryName("x".repeat(BeneficiaryName.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidBeneficiaryNameException.class);
    }
}
