package dev.rsvpfactory.acceptance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock scenarios can move, so "after the RSVP cutoff" doesn't mean waiting for real time to pass. */
final class MutableClock extends Clock {

    private volatile Instant instant;

    MutableClock(Instant instant) {
        this.instant = instant;
    }

    void setInstant(Instant instant) {
        this.instant = instant;
    }

    void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The acceptance clock is always UTC");
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
