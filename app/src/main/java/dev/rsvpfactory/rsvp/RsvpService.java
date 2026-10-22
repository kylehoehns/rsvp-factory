package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.events.EventDetails;
import dev.rsvpfactory.events.EventStatus;
import dev.rsvpfactory.events.Events;
import dev.rsvpfactory.groups.GroupAccess;
import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RsvpService {

    private final Events events;
    private final GroupAccess groups;
    private final SeatingPlanRepository plans;
    private final RsvpRepository rsvps;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    RsvpService(
            Events events,
            GroupAccess groups,
            SeatingPlanRepository plans,
            RsvpRepository rsvps,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.events = events;
        this.groups = groups;
        this.plans = plans;
        this.rsvps = rsvps;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Creates the member's RSVP for their party, or returns the one they already have for that party. */
    @Transactional
    RsvpView rsvp(Member member, long eventId, int guests) {
        SeatingPlan plan = plans.lock(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        EventDetails event = events.find(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        if (event.status() == EventStatus.CANCELLED) {
            throw new RuleBrokenException(
                    "event-cancelled", HttpStatus.CONFLICT, "This event has been cancelled and takes no RSVPs.");
        }
        if (!groups.isMember(event.groupId(), member.id())) {
            throw new RuleBrokenException(
                    "not-a-member", HttpStatus.FORBIDDEN, "Join the group before you RSVP to its events.");
        }
        Seating.State before = rsvps.state(eventId);
        Party party = new Party(member.id(), 1 + guests);
        Seating.Outcome outcome;
        try {
            outcome = Seating.rsvp(plan, before, party, Instant.now(clock));
        } catch (SeatingRefusal refusal) {
            throw new RuleBrokenException(refusal.rule(), refusal.status(), refusal.reason());
        }
        int position = outcome.state().waitlistPosition(member.id());
        if (!outcome.state().equals(before)) {
            rsvps.insert(eventId, party, outcome.placement());
            publisher.publishEvent(
                    outcome.placement() == Placement.CONFIRMED
                            ? new RsvpConfirmed(eventId, member.id(), party.size())
                            : new RsvpWaitlisted(eventId, member.id(), party.size(), position));
        }
        return RsvpView.of(outcome.placement(), party.size(), position);
    }

    /** The member's own RSVP to the event. */
    @Transactional(readOnly = true)
    RsvpView mine(Member member, long eventId) {
        if (events.find(eventId).isEmpty()) {
            throw RuleBrokenException.notFound("event", eventId);
        }
        Seating.State state = rsvps.state(eventId);
        int position = state.waitlistPosition(member.id());
        if (position > 0) {
            return RsvpView.of(
                    Placement.WAITLISTED, state.waitlist().get(position - 1).size(), position);
        }
        return state.confirmed().stream()
                .filter(party -> party.memberId() == member.id())
                .findFirst()
                .map(party -> RsvpView.of(Placement.CONFIRMED, party.size(), 0))
                .orElseThrow(() -> RuleBrokenException.notFound("rsvp", eventId));
    }

    /** Withdraws the member's RSVP, confirmed or waitlisted, and promotes from the waitlist into the seats it frees. */
    @Transactional
    void withdraw(Member member, long eventId) {
        if (events.find(eventId).isEmpty()) {
            throw RuleBrokenException.notFound("event", eventId);
        }
        SeatingPlan plan = plans.lock(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        Seating.State before = rsvps.state(eventId);
        Seating.Withdrawal withdrawal;
        try {
            withdrawal = Seating.withdraw(plan, before, member.id(), Instant.now(clock));
        } catch (SeatingRefusal refusal) {
            throw RuleBrokenException.notFound("rsvp", eventId);
        }
        Party withdrawn = before.confirmed().stream()
                .filter(party -> party.memberId() == member.id())
                .findFirst()
                .orElseGet(() -> before.waitlist().stream()
                        .filter(party -> party.memberId() == member.id())
                        .findFirst()
                        .orElseThrow());
        rsvps.delete(eventId, member.id());
        publisher.publishEvent(new RsvpWithdrawn(eventId, member.id(), withdrawn.size()));
        for (Party promoted : withdrawal.promoted()) {
            rsvps.confirm(eventId, promoted.memberId());
            publisher.publishEvent(new RsvpPromoted(eventId, promoted.memberId(), promoted.size()));
        }
    }
}
