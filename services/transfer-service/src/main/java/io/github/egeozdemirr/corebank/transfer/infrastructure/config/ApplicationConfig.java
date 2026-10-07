package io.github.egeozdemirr.corebank.transfer.infrastructure.config;

import io.github.egeozdemirr.corebank.transfer.domain.money.Currencies;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.policy.BusinessCalendar;
import io.github.egeozdemirr.corebank.transfer.domain.policy.CurrencyPolicy;
import io.github.egeozdemirr.corebank.transfer.domain.policy.TransferPolicy;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TransferPolicyProperties.class)
class ApplicationConfig {

    /**
     * Services ask the clock for "now" so tests can fix time; all timestamps are UTC. It ticks in microseconds because
     * PostgreSQL stores no finer digits, so a timestamp equals its stored copy (ADR-0004).
     */
    @Bean
    Clock clock() {
        return microsecondClock(Clock.systemUTC());
    }

    static Clock microsecondClock(Clock base) {
        return Clock.tick(base, Duration.of(1, ChronoUnit.MICROS));
    }

    @Bean
    BusinessCalendar businessCalendar(TransferPolicyProperties properties) {
        return new BusinessCalendar(properties.businessZone());
    }

    /** Inconsistent limits (threshold above single limit above daily limit) stop the service here, at startup. */
    @Bean
    TransferPolicy transferPolicy(TransferPolicyProperties properties) {
        return new TransferPolicy(properties.currencies().entrySet().stream()
                .map(ApplicationConfig::currencyPolicy)
                .toList());
    }

    @Bean
    OpenAPI transferServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("transfer-service")
                .description("Transfers with maker-checker approval, limits and an idempotent posting in "
                        + "account-service. A 201 response means the transfer was recorded, not that it succeeded: "
                        + "read its status. All data is synthetic.")
                .version("v1"));
    }

    private static CurrencyPolicy currencyPolicy(Map.Entry<String, TransferPolicyProperties.CurrencyLimits> entry) {
        Currency currency = Currencies.fromCode(entry.getKey());
        TransferPolicyProperties.CurrencyLimits limits = entry.getValue();
        return new CurrencyPolicy(Money.of(limits.approvalThreshold(), currency),
                Money.of(limits.singleTransactionLimit(), currency), Money.of(limits.dailyLimit(), currency));
    }
}
