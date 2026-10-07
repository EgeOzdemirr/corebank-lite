package io.github.egeozdemirr.corebank.transfer.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Serves {@link FakeLedgerService} on the test's in-process gRPC server ({@code @AutoConfigureTestGrpcTransport}). */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAccountServiceConfiguration {

    @Bean
    FakeLedgerService fakeLedgerService() {
        return new FakeLedgerService();
    }
}
