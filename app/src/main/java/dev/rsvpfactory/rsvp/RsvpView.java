package dev.rsvpfactory.rsvp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** A member's RSVP to an event; the waitlist position is only there while the RSVP is waitlisted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record RsvpView(String status, int partySize, @Nullable Integer waitlistPosition) {

    static RsvpView of(Placement placement, int partySize, int waitlistPosition) {
        return new RsvpView(
                placement.name().toLowerCase(Locale.ROOT),
                partySize,
                placement == Placement.WAITLISTED ? waitlistPosition : null);
    }
}
