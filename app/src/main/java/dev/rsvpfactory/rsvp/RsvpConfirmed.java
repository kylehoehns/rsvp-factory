package dev.rsvpfactory.rsvp;

/** Published when an RSVP is confirmed: the member's party holds {@code partySize} seats. */
public record RsvpConfirmed(long eventId, long memberId, int partySize) {}
