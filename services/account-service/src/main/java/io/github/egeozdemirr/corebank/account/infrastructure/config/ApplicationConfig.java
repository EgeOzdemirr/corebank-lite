package io.github.egeozdemirr.corebank.account.infrastructure.config;

import io.github.egeozdemirr.corebank.account.infrastructure.iban.IbanProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IbanProperties.class)
class ApplicationConfig {

    /**
     * Services ask the clock for "now" so tests can fix time; all timestamps are UTC. It ticks in microseconds because
     * PostgreSQL stores no finer digits: an instant with nanoseconds (the JDK clock on Linux) would differ from its
     * stored copy, so a repeated request would report a different time than the first one (ADR-0004).
     */
    @Bean
    Clock clock() {
        return microsecondClock(Clock.systemUTC());
    }

    static Clock microsecondClock(Clock base) {
        return Clock.tick(base, Duration.of(1, ChronoUnit.MICROS));
    }

    @Bean
    OpenAPI accountServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("account-service")
                .description("Accounts, double-entry ledger and balances. All data is synthetic.")
                .version("v1"));
    }
}
