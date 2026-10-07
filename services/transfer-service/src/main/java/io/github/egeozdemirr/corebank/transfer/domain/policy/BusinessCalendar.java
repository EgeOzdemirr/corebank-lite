package io.github.egeozdemirr.corebank.transfer.domain.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Decides which business day an instant belongs to. Daily limits count per day of the bank's time zone
 * (Europe/Istanbul by configuration), not per UTC day: Istanbul is UTC+3, so 21:00 UTC already belongs to the next
 * business day.
 */
public record BusinessCalendar(ZoneId zone) {

    public BusinessCalendar {
        Objects.requireNonNull(zone, "zone");
    }

    public LocalDate businessDayOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }
}
