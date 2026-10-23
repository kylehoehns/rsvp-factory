package dev.rsvpfactory.rsvp;

import static dev.rsvpfactory.rsvp.MemberIds.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rsvpfactory.problems.RuleBrokenException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Seam B: a member withdrawing, as a pure function. */
class SeatingWithdrawalTests {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2030-03-08T12:00:00Z");

    private static SeatingPlan plan(int capacity, int guestLimit) {
        return new SeatingPlan(capacity, guestLimit, CUTOFF);
    }

    private static Seating.State state(List<Party> confirmed, List<Party> waitlist) {
        return new Seating.State(confirmed, waitlist);
    }

    @Test
    void withdrawingAConfirmedRsvpPromotesTheEarliestWaitlistedPartyThatFits() {
        Seating.State state = state(
                List.of(new Party(member(1), 3), new Party(member(2), 2)),
                List.of(new Party(member(3), 1), new Party(member(4), 2)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(5, 2), state, member(1), NOW);

        assertThat(withdrawal.promoted()).containsExactly(new Party(member(3), 1), new Party(member(4), 2));
        assertThat(withdrawal.state().confirmed())
                .containsExactly(new Party(member(2), 2), new Party(member(3), 1), new Party(member(4), 2));
        assertThat(withdrawal.state().waitlist()).isEmpty();
    }

    @Test
    void aLargerPartyAheadThatDoesNotFitKeepsItsPlaceAtTheHead() {
        Seating.State state = state(
                List.of(new Party(member(1), 3), new Party(member(2), 2)),
                List.of(new Party(member(3), 4), new Party(member(4), 1)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(5, 3), state, member(1), NOW);

        assertThat(withdrawal.promoted()).containsExactly(new Party(member(4), 1));
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(member(3), 4));
        assertThat(withdrawal.state().waitlistPosition(member(3))).isEqualTo(1);
    }

    @Test
    void promotionRepeatsUntilNoWaitlistedPartyFits() {
        Seating.State state = state(
                List.of(new Party(member(1), 4)),
                List.of(
                        new Party(member(2), 2),
                        new Party(member(3), 3),
                        new Party(member(4), 1),
                        new Party(member(5), 1)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(4, 3), state, member(1), NOW);

        assertThat(withdrawal.promoted())
                .containsExactly(new Party(member(2), 2), new Party(member(4), 1), new Party(member(5), 1));
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(member(3), 3));
    }

    @Test
    void withdrawingAWaitlistedRsvpPromotesNobodyAndFreesNoSeats() {
        Seating.State state =
                state(List.of(new Party(member(1), 3)), List.of(new Party(member(2), 3), new Party(member(3), 3)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(3, 2), state, member(2), NOW);

        assertThat(withdrawal.promoted()).isEmpty();
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(member(3), 3));
        assertThat(withdrawal.state().confirmed()).containsExactly(new Party(member(1), 3));
    }

    @Test
    void withdrawingIsAllowedAtOrAfterTheCutoffButPromotesNobody() {
        Seating.State state = state(List.of(new Party(member(1), 2)), List.of(new Party(member(2), 1)));

        Seating.Withdrawal withdrawal = Seating.withdraw(plan(2, 1), state, member(1), CUTOFF);

        assertThat(withdrawal.promoted()).isEmpty();
        assertThat(withdrawal.state().confirmed()).isEmpty();
        assertThat(withdrawal.state().waitlist()).containsExactly(new Party(member(2), 1));
    }

    @Test
    void withdrawingJustBeforeTheCutoffStillPromotes() {
        Seating.State state = state(List.of(new Party(member(1), 2)), List.of(new Party(member(2), 1)));

        assertThat(Seating.withdraw(plan(2, 1), state, member(1), CUTOFF.minusSeconds(1))
                        .promoted())
                .containsExactly(new Party(member(2), 1));
    }

    @Test
    void withdrawingWithoutAnRsvpIsRefused() {
        assertThatThrownBy(() -> Seating.withdraw(plan(2, 1), Seating.State.EMPTY, member(1), NOW))
                .isInstanceOfSatisfying(
                        RuleBrokenException.class,
                        e -> assertThat(e)
                                .returns("rsvp-not-found", RuleBrokenException::rule)
                                .returns(HttpStatus.NOT_FOUND, RuleBrokenException::status)
                                .returns("You have no RSVP to this event.", RuleBrokenException::getMessage));
    }

    @Property
    void aPromotionNeverSkipsAWaitlistedPartyThatFitsInFavourOfOneBehindIt(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests, @ForAll int withdrawer) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
                    .state();
        }
        if (state.confirmed().isEmpty()) {
            return;
        }
        UUID leaving = state.confirmed()
                .get(Math.floorMod(withdrawer, state.confirmed().size()))
                .memberId();

        Seating.Withdrawal withdrawal = Seating.withdraw(plan, state, leaving, NOW);

        assertThat(withdrawal.state().confirmedSeats()).isLessThanOrEqualTo(plan.capacity());
        int open = withdrawal.state().openSeats(plan);
        for (Party waiting : withdrawal.state().waitlist()) {
            assertThat(waiting.size()).isGreaterThan(open);
        }
        // Promoted parties keep their arrival order.
        List<UUID> arrival = state.waitlist().stream().map(Party::memberId).toList();
        List<UUID> promotedIds =
                withdrawal.promoted().stream().map(Party::memberId).toList();
        assertThat(promotedIds).isSortedAccordingTo(java.util.Comparator.comparingInt(arrival::indexOf));
    }

    @Property
    void withdrawingAndReAddingTheSamePartyRestoresTheOpenSeats(
            @ForAll("plans") SeatingPlan plan, @ForAll("guestCounts") List<Integer> guests) {
        Seating.State state = Seating.State.EMPTY;
        for (int i = 0; i < guests.size(); i++) {
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
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
            state = Seating.rsvp(plan, state, new Party(member(i), 1 + Math.min(guests.get(i), plan.guestLimit())), NOW)
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
