package dev.rsvpfactory.rsvp;

import jakarta.validation.constraints.NotNull;

/** The capacity an organizer wants the event to have. */
record CapacityRequest(@NotNull Integer capacity) {}
