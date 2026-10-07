package io.github.egeozdemirr.corebank.account.api.grpc;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import io.github.egeozdemirr.corebank.account.domain.exception.DomainException;
import io.github.egeozdemirr.corebank.account.domain.exception.ErrorCategory;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * The single place where exceptions become gRPC statuses, the twin of the HTTP {@code GlobalExceptionHandler}.
 * Every status carries a {@code google.rpc.ErrorInfo} whose reason is the stable error code, so transfer-service can
 * tell a definite business rejection from an unknown outcome. Domain exceptions are mapped by category.
 */
@Component
public class GrpcExceptionTranslator implements GrpcExceptionHandler {

    static final String ERROR_DOMAIN = "account-service";
    static final String INVALID_REQUEST = "INVALID_REQUEST";
    static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final Logger LOG = LoggerFactory.getLogger(GrpcExceptionTranslator.class);

    private static final Map<ErrorCategory, Status.Code> CODE_BY_CATEGORY = Map.of(
            ErrorCategory.INVALID_INPUT, Status.Code.INVALID_ARGUMENT,
            ErrorCategory.NOT_FOUND, Status.Code.NOT_FOUND,
            ErrorCategory.RULE_VIOLATION, Status.Code.FAILED_PRECONDITION,
            ErrorCategory.CONFLICT, Status.Code.ALREADY_EXISTS);

    @Override
    public StatusException handleException(Throwable exception) {
        return switch (exception) {
            case DomainException domain -> rejected(domain);
            case InvalidGrpcRequestException invalid ->
                    status(Status.Code.INVALID_ARGUMENT, INVALID_REQUEST, invalid.getMessage());
            case OptimisticLockingFailureException conflict -> {
                LOG.info("Concurrent modification detected: {}", conflict.getMessage());
                yield status(Status.Code.ABORTED, CONCURRENT_MODIFICATION,
                        "The account was changed by another request; retry.");
            }
            default -> {
                LOG.error("Unexpected error in gRPC call", exception);
                yield status(Status.Code.INTERNAL, INTERNAL_ERROR, "An unexpected error occurred.");
            }
        };
    }

    static Status.Code codeFor(ErrorCategory category) {
        return CODE_BY_CATEGORY.get(category);
    }

    private static StatusException rejected(DomainException exception) {
        LOG.info("gRPC request rejected: {} - {}", exception.errorCode(), exception.getMessage());
        return status(codeFor(exception.category()), exception.errorCode(), exception.getMessage());
    }

    private static StatusException status(Status.Code code, String errorCode, String message) {
        ErrorInfo errorInfo = ErrorInfo.newBuilder().setReason(errorCode).setDomain(ERROR_DOMAIN).build();
        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(code.value())
                .setMessage(message)
                .addDetails(Any.pack(errorInfo))
                .build();
        return StatusProto.toStatusException(status);
    }
}
