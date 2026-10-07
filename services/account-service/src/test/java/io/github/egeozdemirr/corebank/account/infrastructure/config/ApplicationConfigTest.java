package io.github.egeozdemirr.corebank.account.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Regression test for a CI-only failure: the JDK clock has nanosecond digits on Linux but not on macOS, and
 * PostgreSQL keeps microseconds, so a retried PostTransfer reported a different posted_at than the first call.
 * A fixed base clock with nanoseconds reproduces the Linux behaviour on every platform.
 */
class ApplicationConfigTest {

    private static final Instant WITH_NANOSECONDS = Instant.parse("2026-10-07T09:30:00.123456789Z");

    @Test
    void clock_dropsDigitsThatPostgresCannotStore() {
        Clock clock = ApplicationConfig.microsecondClock(Clock.fixed(WITH_NANOSECONDS, ZoneOffset.UTC));

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-10-07T09:30:00.123456Z"));
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void applicationClock_isUtcWithMicrosecondPrecision() {
        Clock clock = new ApplicationConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(clock.instant().getNano() % 1_000).isZero();
    }
}
