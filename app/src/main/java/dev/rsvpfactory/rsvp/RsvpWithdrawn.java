package dev.rsvpfactory.rsvp;

/** Published when a member withdraws their RSVP, freeing the {@code partySize} seats it held (if it was confirmed). */
public record RsvpWithdrawn(long eventId, long memberId, int partySize) {}
