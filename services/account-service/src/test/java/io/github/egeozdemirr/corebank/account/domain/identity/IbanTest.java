package io.github.egeozdemirr.corebank.account.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbanTest {

    /** Check digits computed independently (Python, ISO 13616) for bank code 99999 and account number 1. */
    private static final String KNOWN_VALID = "TR809999900000000000000001";

    @Test
    void forTurkishAccount_computesIsoCheckDigits() {
        assertThat(Iban.forTurkishAccount("99999", 1).value()).isEqualTo(KNOWN_VALID);
        assertThat(Iban.forTurkishAccount("99999", 2).value()).isEqualTo("TR539999900000000000000002");
    }

    @Test
    void forTurkishAccount_roundTripsForLargeAccountNumbers() {
        Iban iban = Iban.forTurkishAccount("99999", 9_999_999_999_999_999L);

        assertThat(iban.value()).hasSize(26).endsWith("9999999999999999");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "123456", "abcde"})
    void forTurkishAccount_rejectsMalformedBankCode(String bankCode) {
        assertThatThrownBy(() -> Iban.forTurkishAccount(bankCode, 1)).isInstanceOf(InvalidIbanException.class);
    }

    @Test
    void forTurkishAccount_rejectsNegativeAccountNumber() {
        assertThatThrownBy(() -> Iban.forTurkishAccount("99999", -1)).isInstanceOf(InvalidIbanException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "TR819999900000000000000001",
        "DE809999900000000000000001",
        "TR80999990000000000000001",
        "TR8099999000000000000000AB",
        ""
    })
    void constructor_rejectsInvalidIban(String candidate) {
        assertThatThrownBy(() -> new Iban(candidate)).isInstanceOf(InvalidIbanException.class);
    }

    @Test
    void parse_acceptsPrintedForm() {
        assertThat(Iban.parse("tr80 9999 9000 0000 0000 0000 01").value()).isEqualTo(KNOWN_VALID);
    }

    @Test
    void toString_isMaskedForLogs() {
        Iban iban = new Iban(KNOWN_VALID);

        assertThat(iban.toString()).isEqualTo("TR80******************0001").doesNotContain("99999");
        assertThat(iban.masked()).isEqualTo(iban.toString());
    }
}
