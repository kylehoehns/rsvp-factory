package dev.rsvpfactory.events;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Where an event is in its lifecycle; the API spells it in lower case. */
public enum EventStatus {
    SCHEDULED,
    CANCELLED;

    @JsonValue
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
