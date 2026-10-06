package io.github.egeozdemirr.corebank.account.domain.money;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.TRY;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.exception.CurrencyMismatchException;
import io.github.egeozdemirr.corebank.account.domain.exception.InvalidAmountException;
import io.github.egeozdemirr.corebank.account.domain.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
        "10.005, 10.00",
        "10.015, 10.02",
        "10.025, 10.02",
        "-10.005, -10.00",
        "7, 7.00"
    })
    void amount_isScaledToTwoDigitsWithBankersRounding(String raw, String expected) {
        assertThat(Money.of(raw, TRY).amount()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void equality_ignoresInputScale() {
        assertThat(Money.of("10", TRY)).isEqualTo(Money.of(new BigDecimal("10.000"), TRY));
    }

    @Test
    void plusAndMinus_keepCurrency() {
        Money sum = Money.of("10.10", TRY).plus(Money.of("0.20", TRY));

        assertThat(sum).isEqualTo(Money.of("10.30", TRY));
        assertThat(sum.minus(Money.of("10.30", TRY)).isZero()).isTrue();
    }

    @Test
    void arithmetic_acrossCurrencies_isRejected() {
        Money lira = Money.of("1.00", TRY);
        Money dollar = Money.of("1.00", USD);

        assertThatThrownBy(() -> lira.plus(dollar)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> lira.minus(dollar)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void signPredicates_describeTheAmount() {
        assertThat(Money.of("0.01", TRY).isPositive()).isTrue();
        assertThat(Money.of("0.01", TRY).negate().isNegative()).isTrue();
        assertThat(Money.zero(TRY).isZero()).isTrue();
    }

    @Test
    void requirePositive_rejectsZeroAndNegative() {
        assertThatThrownBy(() -> Money.zero(TRY).requirePositive()).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of("-1", TRY).requirePositive()).isInstanceOf(InvalidAmountException.class);
        assertThat(Money.of("1", TRY).requirePositive()).isEqualTo(Money.of("1", TRY));
    }

    @Test
    void requireNotNegative_acceptsZero() {
        assertThat(Money.zero(TRY).requireNotNegative().isZero()).isTrue();
        assertThatThrownBy(() -> Money.of("-0.01", TRY).requireNotNegative())
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void toString_showsPlainAmountAndCode() {
        assertThat(Money.of("1500", TRY)).hasToString("1500.00 TRY");
    }

    @Test
    void currencies_rejectUnknownCode() {
        assertThat(Currencies.fromCode("EUR").getCurrencyCode()).isEqualTo("EUR");
        assertThatThrownBy(() -> Currencies.fromCode("XYZ"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
