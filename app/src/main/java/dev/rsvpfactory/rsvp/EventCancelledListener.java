package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.events.EventCancelled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ends every RSVP to an event when it is cancelled, in the cancelling transaction and without promoting anyone. */
@Component
class EventCancelledListener {

    private final SeatingPlanRepository plans;
    private final RsvpRepository rsvps;

    EventCancelledListener(SeatingPlanRepository plans, RsvpRepository rsvps) {
        this.plans = plans;
        this.rsvps = rsvps;
    }

    @EventListener
    void on(EventCancelled cancelled) {
        // Wait for any RSVP being seated right now, so none slips in after the cancellation.
        plans.lock(cancelled.eventId());
        rsvps.deleteAll(cancelled.eventId());
    }
}
