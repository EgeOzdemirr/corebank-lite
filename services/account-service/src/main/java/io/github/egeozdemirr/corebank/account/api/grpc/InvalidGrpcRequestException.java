package io.github.egeozdemirr.corebank.account.api.grpc;

import java.io.Serial;

/** A gRPC request that cannot even be turned into a command (malformed id or amount); the gRPC twin of a 400. */
public final class InvalidGrpcRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidGrpcRequestException(String message) {
        super(message);
    }

    public InvalidGrpcRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
