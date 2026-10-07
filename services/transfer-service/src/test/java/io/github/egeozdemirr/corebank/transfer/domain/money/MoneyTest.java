package io.github.egeozdemirr.corebank.transfer.domain.money;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TRY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidAmountException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({"10.005, 10.00", "10.015, 10.02", "10.025, 10.02", "7, 7.00"})
    void amount_isScaledToTwoDigitsWithBankersRounding(String raw, String expected) {
        assertThat(Money.of(raw, TRY).amount()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void plus_keepsCurrencyAndRejectsAnother() {
        assertThat(Money.of("10.10", TRY).plus(Money.of("0.20", TRY))).isEqualTo(Money.of("10.30", TRY));
        assertThatThrownBy(() -> Money.of("1.00", TRY).plus(Money.of("1.00", USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void isGreaterThan_isStrictAndPerCurrency() {
        assertThat(Money.of("10.01", TRY).isGreaterThan(Money.of("10.00", TRY))).isTrue();
        assertThat(Money.of("10.00", TRY).isGreaterThan(Money.of("10", TRY))).isFalse();
        assertThatThrownBy(() -> Money.of("1.00", TRY).isGreaterThan(Money.of("1.00", USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void predicatesAndGuards_describeTheAmount() {
        assertThat(Money.zero(TRY).isZero()).isTrue();
        assertThat(Money.of("0.01", TRY).isPositive()).isTrue();
        assertThat(Money.of("0.01", TRY).requirePositive()).isEqualTo(Money.of("0.01", TRY));
        assertThatThrownBy(() -> Money.zero(TRY).requirePositive()).isInstanceOf(InvalidAmountException.class);
        assertThat(Money.of("1.00", TRY).hasCurrency(USD)).isFalse();
    }

    @Test
    void toString_showsAmountAndCurrency() {
        assertThat(Money.of(new BigDecimal("1500"), TRY)).hasToString("1500.00 TRY");
    }

    @Test
    void unknownCurrencyCode_isUnsupported() {
        assertThat(Currencies.fromCode("EUR").getCurrencyCode()).isEqualTo("EUR");
        assertThatThrownBy(() -> Currencies.fromCode("XYZ")).isInstanceOf(UnsupportedCurrencyException.class);
    }
}
