package dev.rsvpfactory.events;

import dev.rsvpfactory.groups.GroupAccess;
import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EventService implements Events {

    private final EventRepository events;
    private final GroupAccess groups;
    private final ApplicationEventPublisher publisher;

    EventService(EventRepository events, GroupAccess groups, ApplicationEventPublisher publisher) {
        this.events = events;
        this.groups = groups;
        this.publisher = publisher;
    }

    /** Schedules an event for the group and publishes {@link EventScheduled} with the seating plan. */
    @Transactional
    EventDetails schedule(Member organizer, long groupId, ScheduleEventRequest request) {
        if (!groups.exists(groupId)) {
            throw new RuleBrokenException(
                    "group-not-found", HttpStatus.NOT_FOUND, "There is no group " + groupId + ".");
        }
        if (!groups.isOrganizer(groupId, organizer.id())) {
            throw new RuleBrokenException(
                    "not-an-organizer",
                    HttpStatus.FORBIDDEN,
                    "Only an organizer of the group can schedule its events.");
        }
        int capacity = request.capacity() == null ? 0 : request.capacity();
        int guestLimit = request.guestLimit() == null ? 0 : request.guestLimit();
        Instant cutoff = request.rsvpCutoff() == null ? request.startsAt() : request.rsvpCutoff();
        if (capacity < 1) {
            throw invalid("capacity-not-positive", "Capacity must be a positive number of seats.");
        }
        if (guestLimit < 0) {
            throw invalid("guest-limit-negative", "The guest limit cannot be negative.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw invalid("end-not-after-start", "The event must end after it starts.");
        }
        if (cutoff.isAfter(request.startsAt())) {
            throw invalid("rsvp-cutoff-after-start", "The RSVP cutoff must be no later than the event's start.");
        }
        String description = request.description() == null ? "" : request.description();
        long id = events.insert(
                groupId, request.title(), description, request.venue(), request.startsAt(), request.endsAt());
        publisher.publishEvent(new EventScheduled(id, groupId, capacity, guestLimit, cutoff));
        return events.find(id).orElseThrow();
    }

    @Override
    public Optional<EventDetails> find(long eventId) {
        return events.find(eventId);
    }

    @Override
    public List<EventDetails> inGroup(long groupId) {
        return events.inGroup(groupId);
    }

    private static RuleBrokenException invalid(String rule, String message) {
        return new RuleBrokenException(rule, HttpStatus.BAD_REQUEST, message);
    }
}
