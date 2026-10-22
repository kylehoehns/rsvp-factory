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

    /**
     * Creates the member's RSVP for their party, or changes the guests of the one they already have, promoting from the
     * waitlist into any seats that frees.
     */
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
            boolean changed = before.confirmed().stream().anyMatch(held -> held.memberId() == member.id())
                    || before.waitlistPosition(member.id()) > 0;
            if (changed) {
                rsvps.changePartySize(eventId, member.id(), party.size());
            } else {
                rsvps.insert(eventId, party, outcome.placement());
                publisher.publishEvent(
                        outcome.placement() == Placement.CONFIRMED
                                ? new RsvpConfirmed(eventId, member.id(), party.size())
                                : new RsvpWaitlisted(eventId, member.id(), party.size(), position));
            }
            for (Party promoted : outcome.promoted()) {
                rsvps.confirm(eventId, promoted.memberId());
                publisher.publishEvent(new RsvpPromoted(eventId, promoted.memberId(), promoted.size()));
            }
        }
        return RsvpView.of(outcome.placement(), party.size(), position);
    }

    /** Sets the event's capacity for an organizer of its group and promotes from the waitlist into new seats. */
    @Transactional
    EventSeatsView changeCapacity(Member member, long eventId, int capacity) {
        EventDetails event = events.find(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        if (!groups.isOrganizer(event.groupId(), member.id())) {
            throw new RuleBrokenException(
                    "not-an-organizer",
                    HttpStatus.FORBIDDEN,
                    "Only an organizer of the group can change its events' capacity.");
        }
        SeatingPlan plan = plans.lock(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        Seating.State before = rsvps.state(eventId);
        Seating.CapacityChange change;
        try {
            change = Seating.changeCapacity(plan, before, capacity, Instant.now(clock));
        } catch (SeatingRefusal refusal) {
            throw new RuleBrokenException(refusal.rule(), refusal.status(), refusal.reason());
        }
        plans.updateCapacity(eventId, capacity);
        for (Party promoted : change.promoted()) {
            rsvps.confirm(eventId, promoted.memberId());
            publisher.publishEvent(new RsvpPromoted(eventId, promoted.memberId(), promoted.size()));
        }
        return EventSeatsView.of(
                event,
                change.plan(),
                new RsvpRepository.Seats(
                        change.state().confirmedSeats(),
                        change.state().waitlist().size()));
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
        if (!withdrawIfPresent(member.id(), eventId)) {
            throw RuleBrokenException.notFound("rsvp", eventId);
        }
    }

    /**
     * Withdraws the member's RSVPs to the group's events that haven't started, with promotion as for any withdrawal.
     */
    @Transactional
    void withdrawUpcoming(long groupId, long memberId) {
        Instant now = Instant.now(clock);
        for (EventDetails event : events.inGroup(groupId)) {
            if (event.status() == EventStatus.SCHEDULED && event.startsAt().isAfter(now)) {
                withdrawIfPresent(memberId, event.id());
            }
        }
    }

    private boolean withdrawIfPresent(long memberId, long eventId) {
        SeatingPlan plan = plans.lock(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        Seating.State before = rsvps.state(eventId);
        Seating.Withdrawal withdrawal;
        try {
            withdrawal = Seating.withdraw(plan, before, memberId, Instant.now(clock));
        } catch (SeatingRefusal refusal) {
            return false;
        }
        Party withdrawn = before.confirmed().stream()
                .filter(party -> party.memberId() == memberId)
                .findFirst()
                .orElseGet(() -> before.waitlist().stream()
                        .filter(party -> party.memberId() == memberId)
                        .findFirst()
                        .orElseThrow());
        rsvps.delete(eventId, memberId);
        publisher.publishEvent(new RsvpWithdrawn(eventId, memberId, withdrawn.size()));
        for (Party promoted : withdrawal.promoted()) {
            rsvps.confirm(eventId, promoted.memberId());
            publisher.publishEvent(new RsvpPromoted(eventId, promoted.memberId(), promoted.size()));
        }
        return true;
    }
}
