package io.github.egeozdemirr.corebank.transfer.domain.identity;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbanTest {

    @Test
    void validIban_isAcceptedAndParsedFromPrintedForm() {
        assertThat(new Iban(SOURCE_IBAN).value()).isEqualTo(SOURCE_IBAN);
        assertThat(Iban.parse("tr80 9999 9000 0000 0000 0000 01")).isEqualTo(new Iban(SOURCE_IBAN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TR819999900000000000000001", "TR8099999000000000000000011", "DE80999990000000000000001",
        "TR80999990000000000000000A"})
    void wrongCheckDigitsLengthCountryOrCharacters_areRejected(String candidate) {
        assertThatThrownBy(() -> new Iban(candidate)).isInstanceOf(InvalidIbanException.class);
    }

    @Test
    void toString_isMaskedSoIbansNeverReachLogs() {
        Iban iban = new Iban(SOURCE_IBAN);

        assertThat(iban.toString()).isEqualTo("TR80******************0001").doesNotContain("99999");
    }
}
