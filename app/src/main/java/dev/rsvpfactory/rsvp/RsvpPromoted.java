package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** Published when a waitlisted RSVP is promoted to confirmed: the member's party now holds {@code partySize} seats. */
public record RsvpPromoted(long eventId, UUID memberId, int partySize) {}
