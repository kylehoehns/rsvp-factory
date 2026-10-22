package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.events.EventScheduled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Stores the seating plan of every event that is scheduled, in the scheduling transaction. */
@Component
class EventSeatingListener {

    private final SeatingPlanRepository plans;

    EventSeatingListener(SeatingPlanRepository plans) {
        this.plans = plans;
    }

    @EventListener
    void on(EventScheduled scheduled) {
        plans.insert(
                scheduled.eventId(),
                new SeatingPlan(scheduled.capacity(), scheduled.guestLimit(), scheduled.rsvpCutoff()));
    }
}
