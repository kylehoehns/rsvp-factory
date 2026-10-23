package dev.rsvpfactory.rsvp;

import static dev.rsvpfactory.rsvp.MemberIds.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Seam B: an organizer changing the capacity, as a pure function. */
class SeatingCapacityTests {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2030-03-08T12:00:00Z");

    private static SeatingPlan plan(int capacity, int guestLimit) {
        return new SeatingPlan(capacity, guestLimit, CUTOFF);
    }

    @Test
    void raisingTheCapacityPromotesTheEarliestWaitlistedPartiesThatFit() {
        Seating.State state = new Seating.State(
                List.of(new Party(member(1), 2)),
                List.of(new Party(member(2), 3), new Party(member(3), 1), new Party(member(4), 1)));

        Seating.CapacityChange change = Seating.changeCapacity(plan(2, 2), state, 4, NOW);

        assertThat(change.plan().capacity()).isEqualTo(4);
        assertThat(change.promoted()).containsExactly(new Party(member(3), 1), new Party(member(4), 1));
        assertThat(change.state().confirmed())
                .containsExactly(new Party(member(1), 2), new Party(member(3), 1), new Party(member(4), 1));
        assertThat(change.state().waitlist()).containsExactly(new Party(member(2), 3));
    }

    @Test
    void raisingTheCapacityAtOrAfterTheCutoffPromotesNobody() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 1)), List.of(new Party(member(2), 1)));

        Seating.CapacityChange change = Seating.changeCapacity(plan(1, 0), state, 5, CUTOFF);

        assertThat(change.plan().capacity()).isEqualTo(5);
        assertThat(change.promoted()).isEmpty();
        assertThat(change.state()).isEqualTo(state);
    }

    @Test
    void loweringTheCapacityToTheConfirmedSeatsIsAllowed() {
        Seating.State state = new Seating.State(
                List.of(new Party(member(1), 2), new Party(member(2), 1)), List.of(new Party(member(3), 1)));

        Seating.CapacityChange change = Seating.changeCapacity(plan(5, 2), state, 3, NOW);

        assertThat(change.plan().capacity()).isEqualTo(3);
        assertThat(change.promoted()).isEmpty();
        assertThat(change.state()).isEqualTo(state);
    }

    @Test
    void loweringTheCapacityBelowTheConfirmedSeatsIsRefused() {
        Seating.State state = new Seating.State(List.of(new Party(member(1), 2), new Party(member(2), 1)), List.of());

        assertThatThrownBy(() -> Seating.changeCapacity(plan(5, 2), state, 2, NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class,
                        e -> assertThat(e)
                                .returns("capacity-below-confirmed-seats", SeatingRefusal::rule)
                                .returns(HttpStatus.CONFLICT, SeatingRefusal::status));
    }

    @Test
    void aCapacityThatIsNotPositiveIsRefused() {
        assertThatThrownBy(() -> Seating.changeCapacity(plan(5, 2), Seating.State.EMPTY, 0, NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class,
                        e -> assertThat(e)
                                .returns("capacity-not-positive", SeatingRefusal::rule)
                                .returns(HttpStatus.BAD_REQUEST, SeatingRefusal::status));
    }

    @Property
    void loweringCapacityBelowConfirmedSeatsIsAlwaysRefused(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests, @ForAll int shortfall) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        int seats = state.confirmedSeats();
        if (seats < 2) {
            return;
        }
        int lowered = seats - 1 - Math.floorMod(shortfall, seats - 1);
        Seating.State held = state;

        assertThatThrownBy(() -> Seating.changeCapacity(plan, held, lowered, NOW))
                .isInstanceOfSatisfying(
                        SeatingRefusal.class, e -> assertThat(e.rule()).isEqualTo("capacity-below-confirmed-seats"));
    }

    @Property
    void raisingCapacityNeverLeavesAFittingPartyWaiting(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests, @ForAll int extra) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }

        Seating.CapacityChange change =
                Seating.changeCapacity(plan, state, plan.capacity() + Math.floorMod(extra, 20), NOW);

        assertThat(change.state().confirmedSeats())
                .isLessThanOrEqualTo(change.plan().capacity());
        for (Party waiting : change.state().waitlist()) {
            assertThat(waiting.size()).isGreaterThan(change.state().openSeats(change.plan()));
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
