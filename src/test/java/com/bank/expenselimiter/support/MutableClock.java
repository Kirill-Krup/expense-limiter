package com.bank.expenselimiter.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the tests can "wind" to replay dated scenarios (limit set on 1 Jan, another on 10 Jan, ...). */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant initial) {
        this(initial, ZoneOffset.UTC);
    }

    private MutableClock(Instant initial, ZoneId zone) {
        this.instant = initial;
        this.zone = zone;
    }

    public void set(Instant newInstant) {
        this.instant = newInstant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
