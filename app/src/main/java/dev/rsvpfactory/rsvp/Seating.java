package dev.rsvpfactory.rsvp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * The seating rules as a pure function: no Spring, no database, no clock of its own. Given an event's seating plan, its
 * confirmed parties and its waitlist (in arrival order), a change and the current time, it returns the new state.
 */
final class Seating {

    private Seating() {}

    /** The confirmed parties and the waitlist, earliest arrival first. */
    record State(List<Party> confirmed, List<Party> waitlist) {

        static final State EMPTY = new State(List.of(), List.of());

        State {
            confirmed = List.copyOf(confirmed);
            waitlist = List.copyOf(waitlist);
        }

        int confirmedSeats() {
            return confirmed.stream().mapToInt(Party::size).sum();
        }

        int openSeats(SeatingPlan plan) {
            return plan.capacity() - confirmedSeats();
        }

        /** The member's place in line counting from 1, or 0 when they are not on the waitlist. */
        int waitlistPosition(long memberId) {
            for (int i = 0; i < waitlist.size(); i++) {
                if (waitlist.get(i).memberId() == memberId) {
                    return i + 1;
                }
            }
            return 0;
        }

        private @Nullable Party confirmedParty(long memberId) {
            return find(confirmed, memberId);
        }

        private @Nullable Party waitlistedParty(long memberId) {
            return find(waitlist, memberId);
        }

        private static @Nullable Party find(List<Party> parties, long memberId) {
            for (Party party : parties) {
                if (party.memberId() == memberId) {
                    return party;
                }
            }
            return null;
        }
    }

    /** The state after a change, and where the changing party ended up. */
    record Outcome(State state, Placement placement) {}

    /** The state after a withdrawal, and the waitlisted parties it promoted, in the order they were promoted. */
    record Withdrawal(State state, List<Party> promoted) {

        Withdrawal {
            promoted = List.copyOf(promoted);
        }
    }

    /**
     * A member's RSVP for their party. Confirmed when the party fits the open seats, otherwise it joins the end of the
     * waitlist. RSVPing again with the same party changes nothing.
     *
     * @throws SeatingRefusal when the party is over the guest limit, the cutoff has passed, or the member already has
     *     an RSVP for a different party
     */
    static Outcome rsvp(SeatingPlan plan, State state, Party party, Instant now) {
        Party held = state.confirmedParty(party.memberId());
        Party existing = held != null ? held : state.waitlistedParty(party.memberId());
        if (existing != null) {
            if (existing.size() != party.size()) {
                throw new SeatingRefusal(
                        "rsvp-party-change-unsupported",
                        HttpStatus.CONFLICT,
                        "Changing the guests of an existing RSVP is not possible yet.");
            }
            return new Outcome(state, held != null ? Placement.CONFIRMED : Placement.WAITLISTED);
        }
        if (party.size() - 1 > plan.guestLimit()) {
            throw new SeatingRefusal(
                    "guest-limit-exceeded",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "This event allows at most " + plan.guestLimit() + " guests per RSVP.");
        }
        if (!now.isBefore(plan.rsvpCutoff())) {
            throw new SeatingRefusal(
                    "rsvp-cutoff-passed", HttpStatus.CONFLICT, "The RSVP cutoff for this event has passed.");
        }
        if (party.size() <= state.openSeats(plan)) {
            List<Party> confirmed = new ArrayList<>(state.confirmed());
            confirmed.add(party);
            return new Outcome(new State(confirmed, state.waitlist()), Placement.CONFIRMED);
        }
        List<Party> waitlist = new ArrayList<>(state.waitlist());
        waitlist.add(party);
        return new Outcome(new State(state.confirmed(), waitlist), Placement.WAITLISTED);
    }

    /**
     * A member taking back their RSVP, confirmed or waitlisted. Always allowed, even after the cutoff. Freed seats go
     * to the earliest waitlisted parties that fit, repeatedly, until none fits; before the cutoff only.
     *
     * @throws SeatingRefusal when the member has no RSVP
     */
    static Withdrawal withdraw(SeatingPlan plan, State state, long memberId, Instant now) {
        if (state.confirmedParty(memberId) == null && state.waitlistedParty(memberId) == null) {
            throw new SeatingRefusal("rsvp-not-found", HttpStatus.NOT_FOUND, "There is no RSVP to withdraw.");
        }
        List<Party> confirmed = new ArrayList<>(state.confirmed());
        confirmed.removeIf(party -> party.memberId() == memberId);
        List<Party> waitlist = new ArrayList<>(state.waitlist());
        waitlist.removeIf(party -> party.memberId() == memberId);

        List<Party> promoted = promote(plan, confirmed, waitlist, now);
        return new Withdrawal(new State(confirmed, waitlist), promoted);
    }

    /** The plan and state after an organizer changes the capacity, and the parties the new seats promoted. */
    record CapacityChange(SeatingPlan plan, State state, List<Party> promoted) {

        CapacityChange {
            promoted = List.copyOf(promoted);
        }
    }

    /**
     * An organizer raising or lowering the capacity. New seats go to the earliest waitlisted parties that fit,
     * repeatedly, before the cutoff only.
     *
     * @throws SeatingRefusal when the capacity is not positive or is below the seats confirmed parties hold
     */
    static CapacityChange changeCapacity(SeatingPlan plan, State state, int capacity, Instant now) {
        if (capacity < 1) {
            throw new SeatingRefusal(
                    "capacity-not-positive", HttpStatus.BAD_REQUEST, "Capacity must be a positive number of seats.");
        }
        if (capacity < state.confirmedSeats()) {
            throw new SeatingRefusal(
                    "capacity-below-confirmed-seats",
                    HttpStatus.CONFLICT,
                    "Confirmed parties hold " + state.confirmedSeats() + " seats; capacity cannot go below that.");
        }
        SeatingPlan changed = new SeatingPlan(capacity, plan.guestLimit(), plan.rsvpCutoff());
        List<Party> confirmed = new ArrayList<>(state.confirmed());
        List<Party> waitlist = new ArrayList<>(state.waitlist());
        List<Party> promoted = promote(changed, confirmed, waitlist, now);
        return new CapacityChange(changed, new State(confirmed, waitlist), promoted);
    }

    /**
     * Moves the earliest waitlisted parties that fit the open seats to confirmed, in place, before the cutoff only.
     * Open seats only shrink, so a party that did not fit earlier in line never fits later: one pass suffices.
     */
    private static List<Party> promote(SeatingPlan plan, List<Party> confirmed, List<Party> waitlist, Instant now) {
        List<Party> promoted = new ArrayList<>();
        if (now.isBefore(plan.rsvpCutoff())) {
            int open =
                    plan.capacity() - confirmed.stream().mapToInt(Party::size).sum();
            for (Party next : List.copyOf(waitlist)) {
                if (next.size() <= open) {
                    open -= next.size();
                    waitlist.remove(next);
                    confirmed.add(next);
                    promoted.add(next);
                }
            }
        }
        return promoted;
    }
}
