package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** Published when a member changes the guests of an RSVP they already have: the party is now {@code partySize}. */
public record RsvpChanged(UUID eventId, UUID memberId, int partySize) {}
