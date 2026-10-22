package dev.rsvpfactory.events;

import java.util.List;
import java.util.Optional;

/** Looks events up, for modules that refer to events by id only. */
public interface Events {

    Optional<EventDetails> find(long eventId);

    /** The group's events, earliest start first. */
    List<EventDetails> inGroup(long groupId);
}
