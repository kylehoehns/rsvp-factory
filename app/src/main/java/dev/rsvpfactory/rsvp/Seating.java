package dev.rsvpfactory.rsvp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

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

    /**
     * The state after a change, where the changing party ended up, and the waitlisted parties the change promoted, in
     * the order they were promoted.
     */
    record Outcome(State state, Placement placement, List<Party> promoted) {

        Outcome {
            promoted = List.copyOf(promoted);
        }

        Outcome(State state, Placement placement) {
            this(state, placement, List.of());
        }
    }

    /** The state after a withdrawal, and the waitlisted parties it promoted, in the order they were promoted. */
    record Withdrawal(State state, List<Party> promoted) {

        Withdrawal {
            promoted = List.copyOf(promoted);
        }
    }

    /**
     * A member's RSVP for their party. Confirmed when the party fits the open seats, otherwise it joins the end of the
     * waitlist. RSVPing again with the same party changes nothing; with a different party it changes the guests of the
     * existing RSVP: a confirmed RSVP may grow only into open seats, a waitlisted one keeps its place in line, and
     * seats a smaller party frees go to the waitlist. Adding guests, like a new RSVP, stops at the cutoff; removing
     * them never does.
     *
     * @throws SeatingRefusal when the party is over the guest limit, the cutoff has passed for a new RSVP or for added
     *     guests, or a confirmed RSVP asks for more seats than are open
     */
    static Outcome rsvp(SeatingPlan plan, State state, Party party, Instant now) {
        Party held = state.confirmedParty(party.memberId());
        Party existing = held != null ? held : state.waitlistedParty(party.memberId());
        if (existing != null && existing.size() == party.size()) {
            return new Outcome(state, held != null ? Placement.CONFIRMED : Placement.WAITLISTED);
        }
        if (party.size() - 1 > plan.guestLimit()) {
            throw new SeatingRefusal(
                    "guest-limit-exceeded", "This event allows at most " + plan.guestLimit() + " guests per RSVP.");
        }
        if (existing != null) {
            return changeGuests(plan, state, party, held != null, now);
        }
        if (!now.isBefore(plan.rsvpCutoff())) {
            throw cutoffPassed();
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
            throw new SeatingRefusal("rsvp-not-found", "There is no RSVP to withdraw.");
        }
        List<Party> confirmed = new ArrayList<>(state.confirmed());
        confirmed.removeIf(party -> party.memberId() == memberId);
        List<Party> waitlist = new ArrayList<>(state.waitlist());
        waitlist.removeIf(party -> party.memberId() == memberId);

        List<Party> promoted = promote(plan, confirmed, waitlist, now);
        return new Withdrawal(new State(confirmed, waitlist), promoted);
    }

    private static Outcome changeGuests(SeatingPlan plan, State state, Party party, boolean confirmedNow, Instant now) {
        List<Party> confirmed = new ArrayList<>(state.confirmed());
        List<Party> waitlist = new ArrayList<>(state.waitlist());
        List<Party> side = confirmedNow ? confirmed : waitlist;
        int index = indexOf(side, party.memberId());
        int added = party.size() - side.get(index).size();
        if (added > 0) {
            if (!now.isBefore(plan.rsvpCutoff())) {
                throw cutoffPassed();
            }
            if (confirmedNow && added > state.openSeats(plan)) {
                throw new SeatingRefusal(
                        "not-enough-open-seats",
                        "There are not enough open seats to add guests; the RSVP stays as it is.");
            }
        }
        side.set(index, party);
        List<Party> promoted = promote(plan, confirmed, waitlist, now);
        return new Outcome(
                new State(confirmed, waitlist),
                confirmedNow || promoted.contains(party) ? Placement.CONFIRMED : Placement.WAITLISTED,
                promoted);
    }

    /**
     * Moves the earliest waitlisted parties that fit the open seats to confirmed, in place; none at or after the
     * cutoff. Returns the parties promoted, in order.
     */
    private static List<Party> promote(SeatingPlan plan, List<Party> confirmed, List<Party> waitlist, Instant now) {
        List<Party> promoted = new ArrayList<>();
        if (now.isBefore(plan.rsvpCutoff())) {
            int open =
                    plan.capacity() - confirmed.stream().mapToInt(Party::size).sum();
            // Open seats only shrink, so a party that did not fit earlier in line never fits later: one pass suffices.
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

    private static int indexOf(List<Party> parties, long memberId) {
        for (int i = 0; i < parties.size(); i++) {
            if (parties.get(i).memberId() == memberId) {
                return i;
            }
        }
        throw new IllegalArgumentException("No party for member " + memberId);
    }

    private static SeatingRefusal cutoffPassed() {
        return new SeatingRefusal("rsvp-cutoff-passed", "The RSVP cutoff for this event has passed.");
    }
}
