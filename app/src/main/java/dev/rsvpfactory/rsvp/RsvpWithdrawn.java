package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** Published when a member withdraws their RSVP, freeing the {@code partySize} seats it held (if it was confirmed). */
public record RsvpWithdrawn(UUID eventId, UUID memberId, int partySize) {}
