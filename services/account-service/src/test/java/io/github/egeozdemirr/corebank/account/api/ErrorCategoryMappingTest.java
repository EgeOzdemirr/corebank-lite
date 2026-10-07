package io.github.egeozdemirr.corebank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.account.domain.exception.ErrorCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** A new error category must get an HTTP status, otherwise its exceptions would fall through as 500. */
class ErrorCategoryMappingTest {

    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void everyCategory_hasAClientErrorStatus(ErrorCategory category) {
        assertThat(GlobalExceptionHandler.statusFor(category)).isNotNull()
                .satisfies(status -> assertThat(status.is4xxClientError()).isTrue());
    }
}
