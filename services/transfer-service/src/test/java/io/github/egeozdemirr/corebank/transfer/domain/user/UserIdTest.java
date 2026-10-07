package io.github.egeozdemirr.corebank.transfer.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidUserIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserIdTest {

    @Test
    void safeIdentifier_isAccepted() {
        assertThat(new UserId("checker.2:ops-team_1")).hasToString("checker.2:ops-team_1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "maker 1", "maker\nforged-log-line", "ayşe"})
    void unsafeOrEmptyIdentifier_isRejected(String candidate) {
        assertThatThrownBy(() -> new UserId(candidate)).isInstanceOf(InvalidUserIdException.class);
    }

    @Test
    void identifierLongerThan64Characters_isRejected() {
        assertThat(new UserId("u".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> new UserId("u".repeat(65))).isInstanceOf(InvalidUserIdException.class);
    }
}
