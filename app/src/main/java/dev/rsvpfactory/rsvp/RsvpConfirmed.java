package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** Published when an RSVP is confirmed: the member's party holds {@code partySize} seats. */
public record RsvpConfirmed(long eventId, UUID memberId, int partySize) {}
