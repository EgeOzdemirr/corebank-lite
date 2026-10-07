package io.github.egeozdemirr.corebank.account.api;

import io.github.egeozdemirr.corebank.account.domain.exception.DomainException;
import io.github.egeozdemirr.corebank.account.domain.exception.ErrorCategory;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where exceptions become HTTP responses (RFC 9457 problem details). Every problem carries a
 * stable {@code errorCode}. Domain exceptions are mapped by category, so new ones need no change here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERROR_CODE_PROPERTY = "errorCode";
    static final String INVALID_REQUEST = "INVALID_REQUEST";
    static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<ErrorCategory, HttpStatus> STATUS_BY_CATEGORY = Map.of(
            ErrorCategory.INVALID_INPUT, HttpStatus.BAD_REQUEST,
            ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCategory.RULE_VIOLATION, HttpStatus.UNPROCESSABLE_CONTENT,
            ErrorCategory.CONFLICT, HttpStatus.CONFLICT);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomainException(DomainException exception) {
        LOG.info("Request rejected: {} - {}", exception.errorCode(), exception.getMessage());
        return problem(statusFor(exception.category()), exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception) {
        LOG.info("Concurrent modification detected: {}", exception.getMessage());
        return problem(HttpStatus.CONFLICT, CONCURRENT_MODIFICATION,
                "The account was changed by another request; retry with fresh data.");
    }

    /** Last resort: details stay in the log, the client only learns that something went wrong. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOG.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "An unexpected error occurred.");
    }

    /** Framework-detected request errors (validation, malformed JSON, bad path variables) get an errorCode too. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        // The base class may create the ProblemDetail body itself, so the code is added to the finished response.
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail
                && statusCode.is4xxClientError()) {
            problemDetail.setProperty(ERROR_CODE_PROPERTY, INVALID_REQUEST);
        }
        return response;
    }

    static HttpStatus statusFor(ErrorCategory category) {
        return STATUS_BY_CATEGORY.get(category);
    }

    private static ProblemDetail problem(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(ERROR_CODE_PROPERTY, errorCode);
        return problemDetail;
    }
}
