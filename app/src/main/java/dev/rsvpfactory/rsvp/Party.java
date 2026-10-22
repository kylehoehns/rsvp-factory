package dev.rsvpfactory.rsvp;

/** A member and their guests, who hold {@code size} seats together under one RSVP. */
record Party(long memberId, int size) {}
