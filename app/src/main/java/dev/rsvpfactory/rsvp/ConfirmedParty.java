package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** A member and the size of the confirmed party holding seats under their RSVP. */
public record ConfirmedParty(UUID memberId, int size) {}
