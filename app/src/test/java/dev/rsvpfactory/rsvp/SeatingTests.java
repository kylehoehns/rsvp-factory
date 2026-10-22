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
    void rsvpingAgainWithAnotherPartyIsRefused() {
        Seating.State state = new Seating.State(List.of(new Party(1, 3)), List.of());

        assertThatThrownBy(() -> Seating.rsvp(plan(5, 2), state, new Party(1, 2), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-party-change-unsupported"));
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
