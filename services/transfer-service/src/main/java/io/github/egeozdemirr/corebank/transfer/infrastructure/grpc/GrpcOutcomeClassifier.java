package io.github.egeozdemirr.corebank.transfer.infrastructure.grpc;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import java.util.Optional;
import java.util.Set;

/**
 * The single implementation of the ADR-0004 rule. A failure is a definite rejection only if its status is
 * INVALID_ARGUMENT, NOT_FOUND or FAILED_PRECONDITION <em>and</em> it carries an ErrorInfo from account-service.
 * Everything else, explicitly including INTERNAL and UNKNOWN and any status produced by the gRPC library or a proxy
 * (no ErrorInfo), leaves the outcome unknown.
 */
final class GrpcOutcomeClassifier {

    static final String ERROR_DOMAIN = "account-service";
    static final String POSTING_ID_CONFLICT = "POSTING_ID_CONFLICT";

    private static final Set<Status.Code> DEFINITE_REJECTIONS =
            Set.of(Status.Code.INVALID_ARGUMENT, Status.Code.NOT_FOUND, Status.Code.FAILED_PRECONDITION);

    enum Kind {
        DEFINITE_REJECTION,
        ID_CONFLICT,
        RETRYABLE,
        UNKNOWN
    }

    /** {@code errorCode} is present only when account-service itself produced the failure. */
    record Classification(Kind kind, Optional<String> errorCode) {
    }

    private GrpcOutcomeClassifier() {
    }

    static Classification classify(Throwable failure) {
        Optional<String> errorCode = accountServiceErrorCode(failure);
        return new Classification(kindOf(Status.fromThrowable(failure).getCode(), errorCode), errorCode);
    }

    static boolean isRetryable(Throwable failure) {
        return classify(failure).kind() == Kind.RETRYABLE;
    }

    private static Kind kindOf(Status.Code code, Optional<String> errorCode) {
        if (errorCode.isEmpty()) {
            return Kind.UNKNOWN;
        }
        if (DEFINITE_REJECTIONS.contains(code)) {
            return Kind.DEFINITE_REJECTION;
        }
        return switch (code) {
            case ALREADY_EXISTS -> POSTING_ID_CONFLICT.equals(errorCode.get()) ? Kind.ID_CONFLICT : Kind.UNKNOWN;
            case ABORTED -> Kind.RETRYABLE;
            default -> Kind.UNKNOWN;
        };
    }

    private static Optional<String> accountServiceErrorCode(Throwable failure) {
        com.google.rpc.Status details = StatusProto.fromThrowable(failure);
        if (details == null) {
            return Optional.empty();
        }
        return details.getDetailsList().stream()
                .filter(detail -> detail.is(ErrorInfo.class))
                .map(GrpcOutcomeClassifier::unpack)
                .flatMap(Optional::stream)
                .filter(info -> ERROR_DOMAIN.equals(info.getDomain()))
                .map(ErrorInfo::getReason)
                .findFirst();
    }

    /** A detail that claims to be an ErrorInfo but does not parse proves nothing, so it counts as absent. */
    private static Optional<ErrorInfo> unpack(Any detail) {
        try {
            return Optional.of(detail.unpack(ErrorInfo.class));
        } catch (InvalidProtocolBufferException unreadable) {
            return Optional.empty();
        }
    }
}
