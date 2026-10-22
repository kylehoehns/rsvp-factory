package dev.rsvpfactory.rsvp;

import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

/** The guests a member brings; none when left out. */
record RsvpRequest(@Nullable @Min(0) Integer guests) {

    int guestCount() {
        return guests == null ? 0 : guests;
    }
}
