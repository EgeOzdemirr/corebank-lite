package io.github.egeozdemirr.corebank.account.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.domain.exception.InvalidTcknException;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TcknTest {

    /** The textbook example of the algorithm; not a real person's number. */
    private static final String KNOWN_VALID = "10000000146";

    @Test
    void constructor_acceptsValidNumber() {
        assertThat(new Tckn(KNOWN_VALID).value()).isEqualTo(KNOWN_VALID);
    }

    @RepeatedTest(50)
    void constructor_acceptsGeneratedSyntheticNumbers() {
        String candidate = SyntheticData.tckn();

        assertThat(new Tckn(candidate).value()).isEqualTo(candidate);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "10000000147",
        "10000000156",
        "00000000146",
        "1000000014",
        "100000001460",
        "1000000014A",
        ""
    })
    void constructor_rejectsInvalidNumber(String candidate) {
        assertThatThrownBy(() -> new Tckn(candidate)).isInstanceOf(InvalidTcknException.class);
    }

    @Test
    void rejection_doesNotLeakTheNumber() {
        assertThatThrownBy(() -> new Tckn("10000000147")).message().doesNotContain("10000000147");
    }

    @Test
    void toString_isMaskedForLogs() {
        assertThat(new Tckn(KNOWN_VALID)).hasToString("100******46");
    }
}
