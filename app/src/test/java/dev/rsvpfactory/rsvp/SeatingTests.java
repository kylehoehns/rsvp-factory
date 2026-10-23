package dev.rsvpfactory.rsvp;

import static dev.rsvpfactory.rsvp.MemberIds.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Seam B: the seating rules as a pure function, with worked examples and invariants. */
class SeatingTests {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2030-03-08T12:00:00Z");

    private static SeatingPlan plan(int capacity, int guestLimit) {
        return new SeatingPlan(capacity, guestLimit, CUTOFF);
    }

    @Test
    void aPartyThatFitsTheOpenSeatsIsConfirmed() {
        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), Seating.State.EMPTY, new Party(member(1), 3), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.state().confirmed()).containsExactly(new Party(member(1), 3));
        assertThat(outcome.state().openSeats(plan(5, 2))).isEqualTo(2);
    }

    @Test
    void aPartyThatFillsTheLastSeatsIsConfirmed() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of());

        assertThat(Seating.rsvp(plan(5, 2), state, new Party(member(2), 2), NOW).placement())
                .isEqualTo(Placement.CONFIRMED);
    }

    @Test
    void aPartyThatDoesNotFitJoinsTheEndOfTheWaitlist() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of(new Party(member(2), 3)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(member(3), 3), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.WAITLISTED);
        assertThat(outcome.state().waitlist()).containsExactly(new Party(member(2), 3), new Party(member(3), 3));
        assertThat(outcome.state().confirmed()).containsExactly(new Party(member(1), 3));
    }

    @Test
    void aSmallPartyDoesNotJumpTheWaitlistOnArrival() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of(new Party(member(2), 3)));

        assertThat(Seating.rsvp(plan(5, 2), state, new Party(member(3), 1), NOW).placement())
                .isEqualTo(Placement.CONFIRMED);
    }

    @Test
    void rsvpingAgainWithTheSamePartyChangesNothing() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of(new Party(member(2), 3)));

        Seating.Outcome confirmed = Seating.rsvp(plan(5, 2), state, new Party(member(1), 3), NOW);
        Seating.Outcome waitlisted = Seating.rsvp(plan(5, 2), state, new Party(member(2), 3), NOW);

        assertThat(confirmed.state()).isEqualTo(state);
        assertThat(confirmed.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(waitlisted.state()).isEqualTo(state);
        assertThat(waitlisted.placement()).isEqualTo(Placement.WAITLISTED);
    }

    @Test
    void rsvpingAgainWithTheSamePartyIsHarmlessEvenAfterTheCutoff() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of());

        assertThat(Seating.rsvp(plan(5, 2), state, new Party(member(1), 3), CUTOFF)
                        .state())
                .isEqualTo(state);
    }

    @Test
    void aConfirmedPartyGrowsIntoOpenSeats() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 1), new Party(member(2), 2)), List.of());

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(member(1), 3), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.state().confirmed()).containsExactly(new Party(member(1), 3), new Party(member(2), 2));
        assertThat(outcome.promoted()).isEmpty();
    }

    @Test
    void aConfirmedPartyThatCannotGrowIsRefusedAndStaysAsItIs() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 1), new Party(member(2), 3)), List.of());

        assertThatThrownBy(() -> Seating.rsvp(plan(5, 2), state, new Party(member(1), 3), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("not-enough-open-seats"));
    }

    @Test
    void aConfirmedPartyThatShrinksPromotesTheWaitlistIntoTheFreedSeats() {
        Seating.State state = new Seating.State(
                List.of(new Party(member(1), 4), new Party(member(2), 1)),
                List.of(new Party(member(3), 3), new Party(member(4), 1), new Party(member(5), 1)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 3), state, new Party(member(1), 1), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.promoted()).containsExactly(new Party(member(3), 3));
        assertThat(outcome.state().confirmed())
                .containsExactly(new Party(member(1), 1), new Party(member(2), 1), new Party(member(3), 3));
        assertThat(outcome.state().waitlist()).containsExactly(new Party(member(4), 1), new Party(member(5), 1));
    }

    @Test
    void shrinkingSkipsPartiesThatDoNotFitAndKeepsTheirPlace() {
        Seating.State state = new Seating.State(
                List.of(new Party(member(1), 3), new Party(member(2), 2)),
                List.of(new Party(member(3), 3), new Party(member(4), 1)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(member(1), 2), NOW);

        assertThat(outcome.promoted()).containsExactly(new Party(member(4), 1));
        assertThat(outcome.state().waitlist()).containsExactly(new Party(member(3), 3));
    }

    @Test
    void aWaitlistedPartyKeepsItsPlaceInLineWhenItsGuestsChange() {
        Seating.State state = new Seating.State(
                List.of(new Party(member(1), 5)),
                List.of(new Party(member(2), 2), new Party(member(3), 2), new Party(member(4), 2)));

        Seating.Outcome grown = Seating.rsvp(plan(5, 3), state, new Party(member(3), 4), NOW);
        Seating.Outcome shrunk = Seating.rsvp(plan(5, 3), state, new Party(member(3), 1), NOW);

        assertThat(grown.placement()).isEqualTo(Placement.WAITLISTED);
        assertThat(grown.state().waitlist())
                .containsExactly(new Party(member(2), 2), new Party(member(3), 4), new Party(member(4), 2));
        assertThat(shrunk.placement()).isEqualTo(Placement.WAITLISTED);
        assertThat(shrunk.state().waitlist())
                .containsExactly(new Party(member(2), 2), new Party(member(3), 1), new Party(member(4), 2));
        assertThat(shrunk.promoted()).isEmpty();
    }

    @Test
    void aWaitlistedPartyThatShrinksIntoOpenSeatsIsPromoted() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of(new Party(member(2), 3)));

        Seating.Outcome outcome = Seating.rsvp(plan(5, 2), state, new Party(member(2), 2), NOW);

        assertThat(outcome.placement()).isEqualTo(Placement.CONFIRMED);
        assertThat(outcome.state().confirmed()).containsExactly(new Party(member(1), 3), new Party(member(2), 2));
        assertThat(outcome.state().waitlist()).isEmpty();
    }

    @Test
    void changingGuestsIsHeldToTheGuestLimit() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 1)), List.of());

        assertThatThrownBy(() -> Seating.rsvp(plan(10, 2), state, new Party(member(1), 4), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("guest-limit-exceeded"));
    }

    @Test
    void addingGuestsStopsAtTheCutoffButRemovingThemDoesNot() {
        Seating.State confirmed = new Seating.State(List.of(new Party(member(1), 2)), List.of());
        Seating.State waitlisted =
                new Seating.State(List.of(new Party(member(9), 5)), List.of(new Party(member(2), 2)));

        assertThatThrownBy(() -> Seating.rsvp(plan(10, 3), confirmed, new Party(member(1), 3), CUTOFF))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-cutoff-passed"));
        assertThatThrownBy(() -> Seating.rsvp(plan(5, 3), waitlisted, new Party(member(2), 3), CUTOFF))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("rsvp-cutoff-passed"));
        assertThat(Seating.rsvp(plan(10, 3), confirmed, new Party(member(1), 1), CUTOFF)
                        .state()
                        .confirmed())
                .containsExactly(new Party(member(1), 1));
    }

    @Test
    void removingGuestsAtTheCutoffPromotesNobody() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 3)), List.of(new Party(member(2), 1)));

        Seating.Outcome outcome = Seating.rsvp(plan(3, 2), state, new Party(member(1), 1), CUTOFF);

        assertThat(outcome.promoted()).isEmpty();
        assertThat(outcome.state().waitlist()).containsExactly(new Party(member(2), 1));
    }

    @Test
    void moreGuestsThanTheGuestLimitIsRefused() {
        assertThatThrownBy(() -> Seating.rsvp(plan(10, 2), Seating.State.EMPTY, new Party(member(1), 4), NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class,
                        e -> assertThat(e)
                                .returns("guest-limit-exceeded", SeatingRefusal::rule)
                                .returns(HttpStatus.UNPROCESSABLE_CONTENT, SeatingRefusal::status));
    }

    @Test
    void theGuestLimitItselfIsAllowed() {
        assertThat(Seating.rsvp(plan(10, 2), Seating.State.EMPTY, new Party(member(1), 3), NOW)
                        .placement())
                .isEqualTo(Placement.CONFIRMED);
    }

    @Test
    void anRsvpAtOrAfterTheCutoffIsRefusedButOneJustBeforeIsNot() {
        assertThat(Seating.rsvp(plan(10, 0), Seating.State.EMPTY, new Party(member(1), 1), CUTOFF.minusSeconds(1))
                        .placement())
                .isEqualTo(Placement.CONFIRMED);
        assertThatThrownBy(() -> Seating.rsvp(plan(10, 0), Seating.State.EMPTY, new Party(member(1), 1), CUTOFF))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class,
                        e -> assertThat(e)
                                .returns("rsvp-cutoff-passed", SeatingRefusal::rule)
                                .returns(HttpStatus.CONFLICT, SeatingRefusal::status));
    }

    @Test
    void waitlistPositionsCountFromOneInArrivalOrder() {
        Seating.State state = new Seating.State(List.of(), List.of(new Party(member(2), 1), new Party(member(3), 1)));

        assertThat(state.waitlistPosition(member(2))).isEqualTo(1);
        assertThat(state.waitlistPosition(member(3))).isEqualTo(2);
        assertThat(state.waitlistPosition(member(1))).isZero();
    }

    @Property
    void confirmedSeatsNeverExceedCapacity(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
            assertThat(state.confirmedSeats()).isLessThanOrEqualTo(plan.capacity());
            assertThat(state.openSeats(plan)).isGreaterThanOrEqualTo(0);
        }
    }

    @Property
    void everyPartyIsEitherConfirmedOrWaitlistedExactlyOnce(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        List<UUID> members = new ArrayList<>();
        for (int i = 0; i < guests.size(); i++) {
            members.add(member(i));
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        List<UUID> placed = new ArrayList<>();
        state.confirmed().forEach(p -> placed.add(p.memberId()));
        state.waitlist().forEach(p -> placed.add(p.memberId()));
        assertThat(placed).containsExactlyInAnyOrderElementsOf(members);
    }

    @Property
    void aPartyIsWaitlistedOnlyWhenItDoesNotFit(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            Party party = new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit()));
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
