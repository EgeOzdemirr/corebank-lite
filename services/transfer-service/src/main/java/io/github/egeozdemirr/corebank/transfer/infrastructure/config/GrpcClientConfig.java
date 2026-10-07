package io.github.egeozdemirr.corebank.transfer.infrastructure.config;

import io.github.egeozdemirr.corebank.contracts.grpc.ledger.v1.LedgerServiceGrpc;
import io.github.egeozdemirr.corebank.transfer.infrastructure.grpc.AccountServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/** The stub of account-service's internal ledger API, on the channel {@code account-service} (application.yml). */
@Configuration
@EnableConfigurationProperties(AccountServiceProperties.class)
@ImportGrpcClients(target = "account-service", types = LedgerServiceGrpc.LedgerServiceBlockingStub.class)
class GrpcClientConfig {
}
