package dev.rsvpfactory.events;

import java.time.Instant;
import java.util.UUID;

/** Published when an event is scheduled, carrying the seating plan the organizer chose. */
public record EventScheduled(long eventId, UUID groupId, int capacity, int guestLimit, Instant rsvpCutoff) {}
