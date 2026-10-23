package dev.rsvpfactory.rsvp;

import java.util.UUID;

/** A member and their guests, who hold {@code size} seats together under one RSVP. */
record Party(UUID memberId, int size) {}
