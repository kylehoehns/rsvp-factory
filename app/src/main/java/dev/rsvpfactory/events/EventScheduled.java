package dev.rsvpfactory.events;

import java.time.Instant;

/** Published when an event is scheduled, carrying the seating plan the organizer chose. */
public record EventScheduled(long eventId, long groupId, int capacity, int guestLimit, Instant rsvpCutoff) {}
