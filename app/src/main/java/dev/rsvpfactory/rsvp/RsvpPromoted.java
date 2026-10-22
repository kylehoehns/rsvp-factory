package dev.rsvpfactory.rsvp;

/** Published when a waitlisted RSVP is promoted to confirmed: the member's party now holds {@code partySize} seats. */
public record RsvpPromoted(long eventId, long memberId, int partySize) {}
