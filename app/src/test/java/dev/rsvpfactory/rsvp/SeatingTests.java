package dev.rsvpfactory.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/** Seam B: the seating rules as a pure function, with worked examples and invariants. */
class SeatingTests {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2030-03-08T12:00:00Z");

    private static SeatingPlan plan(int capacity, int guestLimit) {
        return new SeatingPlan(capacity, guestLimit, CUTOFF);
    }

    @Test
    void aPartyThatFitsTheOpenSeatsIsConfirmed() {
        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), Seating.State.EMPTY, new Party(1, 3), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.state().confirmed()).containsExactly(new Party(1, 3));
        assertThat(outcome.state().openSeats(plan(5, 2))).isEqualTo(2);
    }

    @Test
    void aPartyThatFillsTheLastSeatsIsConfirmed() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of());

        assertThat(Seating.rsvp(plan(5, 2), state, new Party(2, 2), NOW).placement())
                .isEqualTo(Placement.CONFIRMED);
    }

    @Test
    void aPartyThatDoesNotFitJoinsTheEndOfTheWaitlist() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of(new Party(2, 3)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(3, 3), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.WAITLISTED);
        assertThat(outcome.state().waitlist()).containsExactly(new Party(2, 3), new Party(3, 3));
        assertThat(outcome.state().confirmed()).containsExactly(new Party(1, 3));
    }

    @Test
    void aSmallPartyDoesNotJumpTheWaitlistOnArrival() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of(new Party(2, 3)));

        assertThat(Seating.rsvp(plan(5, 2), state, new Party(3, 1), NOW).placement())
                .isEqualTo(Placement.CONFIRMED);
    }

    @Test
    void rsvpingAgainWithTheSamePartyChangesNothing() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of(new Party(2, 3)));

        Seating.Outcome confirmed = Seating.rsvp(plan(5, 2), state, new Party(1, 3), NOW);
        Seating.Outcome waitlisted = Seating.rsvp(plan(5, 2), state, new Party(2, 3), NOW);

        assertThat(confirmed.state()).isEqualTo(state);
        assertThat(confirmed.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(waitlisted.state()).isEqualTo(state);
        assertThat(waitlisted.placement()).isEqualTo(Placement.WAITLISTED);
    }

    @Test
    void rsvpingAgainWithTheSamePartyIsHarmlessEvenAfterTheCutoff() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of());

        assertThat(Seating.rsvp(plan(5, 2), state, new Party(1, 3), CUTOFF).state())
                .isEqualTo(state);
    }

    @Test
    void aConfirmedPartyGrowsIntoOpenSeats() {
        Seating.State state = new Seating.State(List.of(new Party(1, 1), new Party(2, 2)), List.of());

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(1, 3), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.state().confirmed()).containsExactly(new Party(1, 3), new Party(2, 2));
        assertThat(outcome.promoted()).isEmpty();
    }

    @Test
    void aConfirmedPartyThatCannotGrowIsRefusedAndStaysAsItIs() {
        Seating.State state = new Seating.State(List.of(new Party(1, 1), new Party(2, 3)), List.of());

        assertThatThrownBy(() -> Seating.rsvp(plan(5, 2), state, new Party(1, 3), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("not-enough-open-seats"));
    }

    @Test
    void aConfirmedPartyThatShrinksPromotesTheWaitlistIntoTheFreedSeats() {
        Seating.State state = new Seating.State(
                List.of(new Party(1, 4), new Party(2, 1)), List.of(new Party(3, 3), new Party(4, 1), new Party(5, 1)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 3), state, new Party(1, 1), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.promoted()).containsExactly(new Party(3, 3));
        assertThat(outcome.state().confirmed()).containsExactly(new Party(1, 1), new Party(2, 1), new Party(3, 3));
        assertThat(outcome.state().waitlist()).containsExactly(new Party(4, 1), new Party(5, 1));
    }

    @Test
    void shrinkingSkipsPartiesThatDoNotFitAndKeepsTheirPlace() {
        Seating.State state =
                new Seating.State(List.of(new Party(1, 3), new Party(2, 2)), List.of(new Party(3, 3), new Party(4, 1)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(1, 2), NOW);

        assertThat(outcome.promoted()).containsExactly(new Party(4, 1));
        assertThat(outcome.state().waitlist()).containsExactly(new Party(3, 3));
    }

    @Test
    void aWaitlistedPartyKeepsItsPlaceInLineWhenItsGuestsChange() {
        Seating.State state =
                new Seating.State(List.of(new Party(1, 5)), List.of(new Party(2, 2), new Party(3, 2), new Party(4, 2)));

        Seating.Outcome grown = Seating.rsvp(plan(5, 3), state, new Party(3, 4), NOW);
        Seating.Outcome shrunk = Seating.rsvp(plan(5, 3), state, new Party(3, 1), NOW);

        assertThat(grown.placement()).isEqualTo(Placement.WAITLISTED);
        assertThat(grown.state().waitlist()).containsExactly(new Party(2, 2), new Party(3, 4), new Party(4, 2));
        assertThat(shrunk.placement()).isEqualTo(Placement.WAITLISTED);
        assertThat(shrunk.state().waitlist()).containsExactly(new Party(2, 2), new Party(3, 1), new Party(4, 2));
        assertThat(shrunk.promoted()).isEmpty();
    }

    @Test
    void aWaitlistedPartyThatShrinksIntoOpenSeatsIsPromoted() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of(new Party(2, 3)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(2, 2), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.state().confirmed()).containsExactly(new Party(1, 3), new Party(2, 2));
        assertThat(outcome.state().waitlist()).isEmpty();
    }

    @Test
    void changingGuestsIsHeldToTheGuestLimit() {
        Seating.State state = new Seating.State(List.of(new Party(1, 1)), List.of());

        assertThatThrownBy(() -> Seating.rsvp(plan(10, 2), state, new Party(1, 4), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("guest-limit-exceeded"));
    }

    @Test
    void addingGuestsStopsAtTheCutoffButRemovingThemDoesNot() {
        Seating.State confirmed = new Seating.State(List.of(new Party(1, 2)), List.of());
        Seating.State waitlisted = new Seating.State(List.of(new Party(9, 5)), List.of(new Party(2, 2)));

        assertThatThrownBy(() -> Seating.rsvp(plan(10, 3), confirmed, new Party(1, 3), CUTOFF))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-cutoff-passed"));
        assertThatThrownBy(() -> Seating.rsvp(plan(5, 3), waitlisted, new Party(2, 3), CUTOFF))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-cutoff-passed"));
        assertThat(Seating.rsvp(plan(10, 3), confirmed, new Party(1, 1), CUTOFF)
                        .state()
                        .confirmed())
                .containsExactly(new Party(1, 1));
    }

    @Test
    void removingGuestsAtTheCutoffPromotesNobody() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of(new Party(2, 1)));

        Seating.Outcome outcome = Seating.rsvp(plan(3, 2), state, new Party(1, 1), CUTOFF);

        assertThat(outcome.promoted()).isEmpty();
        assertThat(outcome.state().waitlist()).containsExactly(new Party(2, 1));
    }

    @Test
    void moreGuestsThanTheGuestLimitIsRefused() {
        assertThatThrownBy(() -> Seating.rsvp(plan(10, 2), Seating.State.EMPTY, new Party(1, 4), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("guest-limit-exceeded"));
    }

    @Test
    void theGuestLimitItselfIsAllowed() {
        assertThat(Seating.rsvp(plan(10, 2), Seating.State.EMPTY, new Party(1, 3), NOW)
                        .placement())
                .isEqualTo(Placement.CONFIRMED);
    }

    @Test
    void anRsvpAtOrAfterTheCutoffIsRefusedButOneJustBeforeIsNot() {
        assertThat(Seating.rsvp(plan(10, 0), Seating.State.EMPTY, new Party(1, 1), CUTOFF.minusSeconds(1))
                        .placement())
                .isEqualTo(Placement.CONFIRMED);
        assertThatThrownBy(() -> Seating.rsvp(plan(10, 0), Seating.State.EMPTY, new Party(1, 1), CUTOFF))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-cutoff-passed"));
    }

    @Test
    void waitlistPositionsCountFromOneInArrivalOrder() {
        Seating.State state = new Seating.State(List.of(), List.of(new Party(2, 1), new Party(3, 1)));

        assertThat(state.waitlistPosition(2)).isEqualTo(1);
        assertThat(state.waitlistPosition(3)).isEqualTo(2);
        assertThat(state.waitlistPosition(1)).isZero();
    }

    @Property
    void confirmedSeatsNeverExceedCapacity(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(i, 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
            assertThat(state.confirmedSeats()).isLessThanOrEqualTo(plan.capacity());
            assertThat(state.openSeats(plan)).isGreaterThanOrEqualTo(0);
        }
    }

    @Property
    void everyPartyIsEitherConfirmedOrWaitlistedExactlyOnce(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < guests.size(); i++) {
            members.add((long) i);
            state = Seating.rsvp(plan, state, new Party(i, 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        List<Long> placed = new ArrayList<>();
        state.confirmed().forEach(p -> placed.add(p.memberId()));
        state.waitlist().forEach(p -> placed.add(p.memberId()));
        assertThat(placed).containsExactlyInAnyOrderElementsOf(members);
    }

    @Property
    void aPartyIsWaitlistedOnlyWhenItDoesNotFit(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            Party party = new Party(i, 1 + Math.min(guests.get(i), plan.guestLimit()));
            int open = state.openSeats(plan);
            Seating.Outcome outcome = Seating.rsvp(plan, state, party, NOW);
            assertThat(outcome.placement() == Placement.CONFIRMED).isEqualTo(party.size() <= open);
            state = outcome.state();
        }
    }

    private static Seating.State state(List<Party> confirmed, List<Party> waitlist) {
        return new Seating.State(confirmed, waitlist);
    }

    @Test
    void withdrawingAConfirmedRsvpPromotesTheEarliestWaitlistedPartyThatFits() {
        Seating.State state =
                state(List.of(new Party(1, 3), new Party(2, 2)), List.of(new Party(3, 1), new Party(4, 2)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(5, 2), state, 1, NOW);

        assertThat(withdrawal.promoted()).containsExactly(new Party(3, 1), new Party(4, 2));
        assertThat(withdrawal.state().confirmed()).containsExactly(new Party(2, 2), new Party(3, 1), new Party(4, 2));
        assertThat(withdrawal.state().waitlist()).isEmpty();
    }

    @Test
    void aLargerPartyAheadThatDoesNotFitKeepsItsPlaceAtTheHead() {
        Seating.State state =
                state(List.of(new Party(1, 3), new Party(2, 2)), List.of(new Party(3, 4), new Party(4, 1)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(5, 3), state, 1, NOW);

        assertThat(withdrawal.promoted()).containsExactly(new Party(4, 1));
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(3, 4));
        assertThat(withdrawal.state().waitlistPosition(3)).isEqualTo(1);
    }

    @Test
    void promotionRepeatsUntilNoWaitlistedPartyFits() {
        Seating.State state = state(
                List.of(new Party(1, 4)), List.of(new Party(2, 2), new Party(3, 3), new Party(4, 1), new Party(5, 1)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(4, 3), state, 1, NOW);

        assertThat(withdrawal.promoted()).containsExactly(new Party(2, 2), new Party(4, 1), new Party(5, 1));
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(3, 3));
    }

    @Test
    void withdrawingAWaitlistedRsvpPromotesNobodyAndFreesNoSeats() {
        Seating.State state = state(List.of(new Party(1, 3)), List.of(new Party(2, 3), new Party(3, 3)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(3, 2), state, 2, NOW);

        assertThat(withdrawal.promoted()).isEmpty();
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(3, 3));
        assertThat(withdrawal.state().confirmed()).containsExactly(new Party(1, 3));
    }

    @Test
    void withdrawingIsAllowedAtOrAfterTheCutoffButPromotesNobody() {
        Seating.State state = state(List.of(new Party(1, 2)), List.of(new Party(2, 1)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(2, 1), state, 1, CUTOFF);

        assertThat(withdrawal.promoted()).isEmpty();
        assertThat(withdrawal.state().confirmed()).isEmpty();
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(2, 1));
    }

    @Test
    void withdrawingJustBeforeTheCutoffStillPromotes() {
        Seating.State state = state(List.of(new Party(1, 2)), List.of(new Party(2, 1)));

        assertThat(Seating.withdraw(plan(2, 1), state, 1, CUTOFF.minusSeconds(1))
                        .promoted())
                .containsExactly(new Party(2, 1));
    }

    @Test
    void withdrawingWithoutAnRsvpIsRefused() {
        assertThatThrownBy(() -> Seating.withdraw(plan(2, 1), Seating.State.EMPTY, 1, NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-not-found"));
    }

    @Property
    void aPromotionNeverSkipsAWaitlistedPartyThatFitsInFavourOfOneBehindIt(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests, @ForAll int withdrawer) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(i, 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        if (state.confirmed().isEmpty()) {
            return;
        }
        long leaving = state.confirmed()
                .get(Math.floorMod(withdrawer, state.confirmed().size()))
                .memberId();

        Seating.Withdrawal withdrawal = Seating.withdraw(plan, state, leaving, NOW);

        assertThat(withdrawal.state().confirmedSeats()).isLessThanOrEqualTo(plan.capacity());
        int open = withdrawal.state().openSeats(plan);
        for (Party waiting : withdrawal.state().waitlist()) {
            assertThat(waiting.size()).isGreaterThan(open);
        }
        // Promoted parties keep their arrival order.
        List<Long> arrival = state.waitlist().stream().map(Party::memberId).toList();
        List<Long> promotedIds =
                withdrawal.promoted().stream().map(Party::memberId).toList();
        assertThat(promotedIds).isSortedAccordingTo(java.util.Comparator.comparingInt(arrival::indexOf));
    }

    @Property
    void withdrawingAndReAddingTheSamePartyRestoresTheOpenSeats(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(i, 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        if (state.confirmed().isEmpty() || !state.waitlist().isEmpty()) {
            return;
        }
        Party party = state.confirmed().getFirst();

        Seating.State without =
                Seating.withdraw(plan, state, party.memberId(), NOW).state();
        Seating.State again = Seating.rsvp(plan, without, party, NOW).state();

        assertThat(again.openSeats(plan)).isEqualTo(state.openSeats(plan));
    }

    @Property
    void nothingIsPromotedAtOrAfterTheCutoff(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(i, 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        if (state.confirmed().isEmpty()) {
            return;
        }

        Seating.Withdrawal withdrawal =
                Seating.withdraw(plan, state, state.confirmed().getFirst().memberId(), CUTOFF);

        assertThat(withdrawal.promoted()).isEmpty();
        assertThat(withdrawal.state().waitlist()).isEqualTo(state.waitlist());
    }

    @Provide
    Arbitrary<SeatingPlan> plans() {
        return Arbitraries.integers()
                .between(1, 30)
                .flatMap(capacity -> Arbitraries.integers()
                        .between(0, 5)
                        .map(guestLimit -> new SeatingPlan(capacity, guestLimit, CUTOFF)));
    }

    @Provide
    Arbitrary<List<Integer>> guestCounts() {
        return Arbitraries.integers().between(0, 5).list().ofMaxSize(30);
    }
}
