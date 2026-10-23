package dev.rsvpfactory.identity;

import java.util.UUID;

/** A person who can sign in. */
public record Member(UUID id, String username) {}
