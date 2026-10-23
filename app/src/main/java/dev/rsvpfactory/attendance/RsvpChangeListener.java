package dev.rsvpfactory.attendance;

import dev.rsvpfactory.rsvp.RsvpChanged;
import dev.rsvpfactory.rsvp.RsvpWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps check-ins in step with RSVP changes, in the changing transaction. */
@Component
class RsvpChangeListener {

    private final CheckInRepository checkIns;

    RsvpChangeListener(CheckInRepository checkIns) {
        this.checkIns = checkIns;
    }

    @EventListener
    void on(RsvpWithdrawn withdrawn) {
        checkIns.delete(withdrawn.eventId(), withdrawn.memberId());
    }

    @EventListener
    void on(RsvpChanged changed) {
        checkIns.capAt(changed.eventId(), changed.memberId(), changed.partySize());
    }
}
