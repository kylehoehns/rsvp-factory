package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.events.EventDetails;
import dev.rsvpfactory.events.EventStatus;
import java.time.Instant;

/** An event with its seats: what anyone may see before deciding to RSVP. */
record EventSeatsView(
        long id,
        long groupId,
        String title,
        String description,
        String venue,
        Instant startsAt,
        Instant endsAt,
        EventStatus status,
        int capacity,
        int guestLimit,
        Instant rsvpCutoff,
        int openSeats,
        int waitlistLength) {

    static EventSeatsView of(EventDetails event, SeatingPlan plan, RsvpRepository.Seats seats) {
        return new EventSeatsView(
                event.id(),
                event.groupId(),
                event.title(),
                event.description(),
                event.venue(),
                event.startsAt(),
                event.endsAt(),
                event.status(),
                plan.capacity(),
                plan.guestLimit(),
                plan.rsvpCutoff(),
                plan.capacity() - seats.confirmedSeats(),
                seats.waitlistLength());
    }
}
