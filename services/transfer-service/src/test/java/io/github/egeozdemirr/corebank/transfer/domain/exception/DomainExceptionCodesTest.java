package io.github.egeozdemirr.corebank.transfer.domain.exception;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TRY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.USD;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Error codes are part of the API: clients branch on them and the API layer maps categories to HTTP statuses.
 * Changing a code or a category must be a deliberate change of this list.
 */
class DomainExceptionCodesTest {

    static Stream<Arguments> everyTransferException() {
        TransferId transferId = TransferId.newId();
        return Stream.of(
                Arguments.of(new InvalidAmountException("zero"), ErrorCategory.INVALID_INPUT, "INVALID_AMOUNT"),
                Arguments.of(new CurrencyMismatchException(TRY, USD), ErrorCategory.INVALID_INPUT,
                        "CURRENCY_MISMATCH"),
                Arguments.of(new UnsupportedCurrencyException("XYZ"), ErrorCategory.INVALID_INPUT,
                        "UNSUPPORTED_CURRENCY"),
                Arguments.of(new InvalidIbanException(), ErrorCategory.INVALID_INPUT, "INVALID_IBAN"),
                Arguments.of(new InvalidUserIdException(), ErrorCategory.INVALID_INPUT, "INVALID_USER_ID"),
                Arguments.of(new InvalidBeneficiaryNameException("blank"), ErrorCategory.INVALID_INPUT,
                        "INVALID_BENEFICIARY_NAME"),
                Arguments.of(new SameAccountTransferException(new AccountId(UUID.randomUUID())),
                        ErrorCategory.INVALID_INPUT, "SAME_ACCOUNT_TRANSFER"),
                Arguments.of(new SingleTransactionLimitExceededException(money("2.00"), money("1.00")),
                        ErrorCategory.RULE_VIOLATION, "SINGLE_TRANSACTION_LIMIT_EXCEEDED"),
                Arguments.of(new DailyLimitExceededException(money("2.00"), money("1.00")),
                        ErrorCategory.RULE_VIOLATION, "DAILY_LIMIT_EXCEEDED"),
                Arguments.of(new MakerCannotApproveException(transferId), ErrorCategory.RULE_VIOLATION,
                        "MAKER_CANNOT_APPROVE"),
                Arguments.of(new InvalidStateTransitionException(transferId, TransferStatus.POSTED,
                        TransferStatus.APPROVED), ErrorCategory.CONFLICT, "INVALID_STATE_TRANSITION"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("everyTransferException")
    void exception_hasItsStableCategoryAndCode(DomainException exception, ErrorCategory category, String code) {
        assertThat(exception.category()).isEqualTo(category);
        assertThat(exception.errorCode()).isEqualTo(code);
        assertThat(exception.getMessage()).isNotBlank();
    }
}
