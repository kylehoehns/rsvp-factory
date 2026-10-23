package dev.rsvpfactory.events;

import java.util.UUID;

/** Published when an organizer cancels an event. */
public record EventCancelled(long eventId, UUID groupId) {}
