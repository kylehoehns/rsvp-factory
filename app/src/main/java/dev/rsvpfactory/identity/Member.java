package dev.rsvpfactory.identity;

/** A signed-in person, as other modules see them. */
public record Member(long id, String username, String name) {}
