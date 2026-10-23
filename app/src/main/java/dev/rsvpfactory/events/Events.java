package dev.rsvpfactory.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Looks events up, for modules that refer to events by id only. */
public interface Events {

    Optional<EventDetails> find(UUID eventId);

    /** The group's events, earliest start first. */
    List<EventDetails> inGroup(UUID groupId);
}
