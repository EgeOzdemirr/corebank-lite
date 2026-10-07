package io.github.egeozdemirr.corebank.account.support;

import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Asks a gRPC server for its services the way grpcurl does, through the reflection API. */
public final class GrpcReflectionProbe {

    private static final long TIMEOUT_SECONDS = 5;
    private static final String ALL_SERVICES = "*";

    private GrpcReflectionProbe() {
    }

    /** The names of the served services; fails with the server's status if reflection is not available. */
    public static List<String> listServices(ServerReflectionGrpc.ServerReflectionStub reflection)
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<List<String>> services = new CompletableFuture<>();
        StreamObserver<ServerReflectionRequest> requests = reflection.serverReflectionInfo(
                new StreamObserver<>() {
                    @Override
                    public void onNext(ServerReflectionResponse response) {
                        services.complete(response.getListServicesResponse().getServiceList().stream()
                                .map(ServiceResponse::getName)
                                .toList());
                    }

                    @Override
                    public void onError(Throwable error) {
                        services.completeExceptionally(error);
                    }

                    @Override
                    public void onCompleted() {
                        // the answer arrives in onNext
                    }
                });
        requests.onNext(ServerReflectionRequest.newBuilder().setListServices(ALL_SERVICES).build());
        requests.onCompleted();
        return services.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
