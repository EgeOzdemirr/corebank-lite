package io.github.egeozdemirr.corebank.transfer.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Istanbul is UTC+3 all year, so the business day changes at 21:00 UTC. */
class BusinessCalendarTest {

    private final BusinessCalendar istanbul = new BusinessCalendar(ZoneId.of("Europe/Istanbul"));

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "2026-10-07T00:00:00Z, 2026-10-07",
        "2026-10-07T20:59:59.999999Z, 2026-10-07",
        "2026-10-07T21:00:00Z, 2026-10-08",
        "2026-10-06T22:30:00Z, 2026-10-07",
        "2026-12-31T21:00:00Z, 2027-01-01",
        "2026-03-29T21:00:00Z, 2026-03-30"
    })
    void instant_belongsToTheIstanbulDay(Instant instant, LocalDate businessDay) {
        assertThat(istanbul.businessDayOf(instant)).isEqualTo(businessDay);
    }
}
