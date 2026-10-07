package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.account.support.GrpcReflectionProbe;
import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.ActiveProfiles;

/** On a developer machine (local profile) tools like grpcurl can discover the API; elsewhere they cannot. */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = ServerReflectionGrpc.ServerReflectionStub.class)
@Import(PostgresContainerConfiguration.class)
class GrpcReflectionLocalProfileIT {

    @Autowired
    private ServerReflectionGrpc.ServerReflectionStub reflection;

    @Test
    void localProfile_servesReflection() throws Exception {
        assertThat(GrpcReflectionProbe.listServices(reflection)).contains("corebank.ledger.v1.LedgerService");
    }
}
