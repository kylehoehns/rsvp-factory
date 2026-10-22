package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.groups.MemberLeftGroup;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Withdraws a leaving member's RSVPs to the group's upcoming events, in the leaving transaction. */
@Component
class MemberLeftGroupListener {

    private final RsvpService rsvps;

    MemberLeftGroupListener(RsvpService rsvps) {
        this.rsvps = rsvps;
    }

    @EventListener
    void on(MemberLeftGroup left) {
        rsvps.withdrawUpcoming(left.groupId(), left.memberId());
    }
}
