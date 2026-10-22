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

    /** The state after a change, and where the changing party ended up. */
    record Outcome(State state, Placement placement) {}

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
                        "Changing the guests of an existing RSVP is not possible yet.");
            }
            return new Outcome(state, held != null ? Placement.CONFIRMED : Placement.WAITLISTED);
        }
        if (party.size() - 1 > plan.guestLimit()) {
            throw new SeatingRefusal(
                    "guest-limit-exceeded", "This event allows at most " + plan.guestLimit() + " guests per RSVP.");
        }
        if (!now.isBefore(plan.rsvpCutoff())) {
            throw new SeatingRefusal("rsvp-cutoff-passed", "The RSVP cutoff for this event has passed.");
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
}
