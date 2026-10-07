package io.github.egeozdemirr.corebank.account.api.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.ErrorInfo;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.ErrorCategory;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.exception.PostingConflictException;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.OptimisticLockingFailureException;

class GrpcExceptionTranslatorTest {

    private final GrpcExceptionTranslator translator = new GrpcExceptionTranslator();

    /** A new error category must get a status code, otherwise its exceptions would fall through as INTERNAL. */
    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void everyCategory_hasAStatusCode(ErrorCategory category) {
        assertThat(GrpcExceptionTranslator.codeFor(category)).isNotNull().isNotEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void businessRejection_isFailedPreconditionWithTheErrorCode() {
        StatusException status = translator.handleException(new InsufficientFundsException(AccountId.newId()));

        assertThat(status.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(errorInfo(status).getReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(errorInfo(status).getDomain()).isEqualTo(GrpcExceptionTranslator.ERROR_DOMAIN);
    }

    @Test
    void reusedPostingId_isAlreadyExists() {
        StatusException status = translator.handleException(new PostingConflictException(PostingId.newId()));

        assertThat(status.getStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
        assertThat(errorInfo(status).getReason()).isEqualTo("POSTING_ID_CONFLICT");
    }

    @Test
    void malformedRequest_isInvalidArgument() {
        StatusException status = translator.handleException(
                new InvalidGrpcRequestException("posting_id must be a UUID"));

        assertThat(status.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(errorInfo(status).getReason()).isEqualTo(GrpcExceptionTranslator.INVALID_REQUEST);
    }

    @Test
    void versionConflict_isAbortedSoTheCallerMayRetry() {
        StatusException status = translator.handleException(new OptimisticLockingFailureException("stale"));

        assertThat(status.getStatus().getCode()).isEqualTo(Status.Code.ABORTED);
        assertThat(errorInfo(status).getReason()).isEqualTo(GrpcExceptionTranslator.CONCURRENT_MODIFICATION);
    }

    @Test
    void unexpectedError_isInternalWithoutDetails() {
        StatusException status = translator.handleException(new IllegalStateException("secret internal detail"));

        assertThat(status.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getStatus().getDescription()).doesNotContain("secret internal detail");
        assertThat(errorInfo(status).getReason()).isEqualTo(GrpcExceptionTranslator.INTERNAL_ERROR);
    }

    private static ErrorInfo errorInfo(StatusException status) {
        try {
            return StatusProto.fromThrowable(status).getDetails(0).unpack(ErrorInfo.class);
        } catch (InvalidProtocolBufferException notErrorInfo) {
            throw new AssertionError("first status detail is not an ErrorInfo", notErrorInfo);
        }
    }
}
