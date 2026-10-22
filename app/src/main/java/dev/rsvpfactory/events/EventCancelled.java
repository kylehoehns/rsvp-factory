package dev.rsvpfactory.events;

/** Published when an organizer cancels an event. */
public record EventCancelled(long eventId, long groupId) {}
