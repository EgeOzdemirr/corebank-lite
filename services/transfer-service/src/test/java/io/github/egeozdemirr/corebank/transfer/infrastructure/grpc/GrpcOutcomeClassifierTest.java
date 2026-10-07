package io.github.egeozdemirr.corebank.transfer.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The ADR-0004 rule: only three codes with account-service's ErrorInfo are definite; nothing else ever is. */
class GrpcOutcomeClassifierTest {

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"INVALID_ARGUMENT", "NOT_FOUND", "FAILED_PRECONDITION"})
    void definiteCodesWithErrorInfo_areDefiniteRejections(Status.Code code) {
        GrpcOutcomeClassifier.Classification classification =
                GrpcOutcomeClassifier.classify(fromAccountService(code, "INSUFFICIENT_FUNDS"));

        assertThat(classification.kind()).isEqualTo(GrpcOutcomeClassifier.Kind.DEFINITE_REJECTION);
        assertThat(classification.errorCode()).contains("INSUFFICIENT_FUNDS");
    }

    /** Every status code without ErrorInfo, the definite ones included, leaves the outcome unknown. */
    @ParameterizedTest
    @EnumSource(Status.Code.class)
    void anyCodeWithoutErrorInfo_isUnknown(Status.Code code) {
        assertThat(GrpcOutcomeClassifier.classify(code.toStatus().asRuntimeException()).kind())
                .isEqualTo(GrpcOutcomeClassifier.Kind.UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"INTERNAL", "UNKNOWN", "UNAVAILABLE", "DEADLINE_EXCEEDED",
        "CANCELLED", "RESOURCE_EXHAUSTED", "UNIMPLEMENTED", "DATA_LOSS"})
    void internalAndTransportCodes_areNeverDefiniteEvenWithErrorInfo(Status.Code code) {
        assertThat(GrpcOutcomeClassifier.classify(fromAccountService(code, "INTERNAL_ERROR")).kind())
                .isEqualTo(GrpcOutcomeClassifier.Kind.UNKNOWN);
    }

    @Test
    void errorInfoFromAnotherDomain_provesNothing() {
        StatusRuntimeException proxy = StatusProto.toStatusRuntimeException(com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.FAILED_PRECONDITION.value())
                .addDetails(Any.pack(ErrorInfo.newBuilder().setReason("INSUFFICIENT_FUNDS").setDomain("proxy").build()))
                .build());

        assertThat(GrpcOutcomeClassifier.classify(proxy).kind()).isEqualTo(GrpcOutcomeClassifier.Kind.UNKNOWN);
    }

    @Test
    void unreadableErrorInfo_provesNothing() {
        StatusRuntimeException garbled = StatusProto.toStatusRuntimeException(com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.NOT_FOUND.value())
                .addDetails(Any.newBuilder().setTypeUrl("type.googleapis.com/google.rpc.ErrorInfo")
                        .setValue(ByteString.copyFromUtf8("not a protobuf message")))
                .build());

        assertThat(GrpcOutcomeClassifier.classify(garbled).kind()).isEqualTo(GrpcOutcomeClassifier.Kind.UNKNOWN);
    }

    @Test
    void postingIdConflict_isAnIdConflictAndNotRetryable() {
        StatusRuntimeException conflict = fromAccountService(Status.Code.ALREADY_EXISTS, "POSTING_ID_CONFLICT");

        assertThat(GrpcOutcomeClassifier.classify(conflict).kind())
                .isEqualTo(GrpcOutcomeClassifier.Kind.ID_CONFLICT);
        assertThat(GrpcOutcomeClassifier.isRetryable(conflict)).isFalse();
    }

    @Test
    void otherAlreadyExists_isUnknown() {
        assertThat(GrpcOutcomeClassifier.classify(fromAccountService(Status.Code.ALREADY_EXISTS, "SOMETHING_ELSE"))
                .kind()).isEqualTo(GrpcOutcomeClassifier.Kind.UNKNOWN);
    }

    @Test
    void abortedFromAccountService_isTheOnlyRetryableCase() {
        StatusRuntimeException aborted = fromAccountService(Status.Code.ABORTED, "CONCURRENT_MODIFICATION");

        assertThat(GrpcOutcomeClassifier.isRetryable(aborted)).isTrue();
        assertThat(GrpcOutcomeClassifier.isRetryable(Status.ABORTED.asRuntimeException())).isFalse();
        assertThat(GrpcOutcomeClassifier.isRetryable(new IllegalStateException("not gRPC"))).isFalse();
        assertThat(GrpcOutcomeClassifier.classify(new IllegalStateException("not gRPC")).errorCode())
                .isEqualTo(Optional.empty());
    }

    private static StatusRuntimeException fromAccountService(Status.Code code, String errorCode) {
        return StatusProto.toStatusRuntimeException(com.google.rpc.Status.newBuilder()
                .setCode(code.value())
                .addDetails(Any.pack(ErrorInfo.newBuilder().setReason(errorCode)
                        .setDomain(GrpcOutcomeClassifier.ERROR_DOMAIN).build()))
                .build());
    }
}
