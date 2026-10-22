package dev.rsvpfactory.rsvp;

import java.time.Instant;

/** An event's capacity, guest limit and RSVP cutoff. */
record SeatingPlan(int capacity, int guestLimit, Instant rsvpCutoff) {}
