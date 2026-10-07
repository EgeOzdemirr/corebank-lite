package io.github.egeozdemirr.corebank.transfer.infrastructure.grpc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How transfer-service talks to account-service (ADR-0005).
 *
 * @param callDeadline  longest a single gRPC call may take
 * @param postingBudget longest all attempts of one posting may take together, retries and waits included; once it is
 *                      spent the outcome counts as unknown and the transfer stays APPROVED
 * @param retry         retries of a posting that account-service aborted (CONCURRENT_MODIFICATION)
 */
@ConfigurationProperties("corebank.transfer.account-service")
public record AccountServiceProperties(Duration callDeadline, Duration postingBudget, Retry retry) {

    public AccountServiceProperties {
        Objects.requireNonNull(callDeadline, "callDeadline");
        Objects.requireNonNull(postingBudget, "postingBudget");
        Objects.requireNonNull(retry, "retry");
    }

    /**
     * Exponential backoff: {@code initialDelay}, then multiplied by {@code multiplier} up to {@code maxDelay}, at most
     * {@code maxRetries} times after the first attempt. The multiplier is a {@code BigDecimal} because the build bans
     * {@code double} declarations (the money rule); it is converted only where Spring's retry policy needs it.
     */
    public record Retry(long maxRetries, Duration initialDelay, BigDecimal multiplier, Duration maxDelay) {

        public Retry {
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(multiplier, "multiplier");
            Objects.requireNonNull(maxDelay, "maxDelay");
        }
    }
}
