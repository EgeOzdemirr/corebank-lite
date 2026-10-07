package io.github.egeozdemirr.corebank.transfer.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A UTC clock that tests move forward, for example past the end of a business day. */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant now) {
        this.now = now;
    }

    public void setInstant(Instant instant) {
        now = instant;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
