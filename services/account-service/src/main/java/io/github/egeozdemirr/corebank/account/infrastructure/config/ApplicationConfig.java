package io.github.egeozdemirr.corebank.account.infrastructure.config;

import io.github.egeozdemirr.corebank.account.infrastructure.iban.IbanProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IbanProperties.class)
class ApplicationConfig {

    /** Services ask the clock for "now" so tests can fix time; all timestamps are UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI accountServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("account-service")
                .description("Accounts, double-entry ledger and balances. All data is synthetic.")
                .version("v1"));
    }
}
