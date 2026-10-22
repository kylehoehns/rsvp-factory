package dev.rsvpfactory.acceptance;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Withdrawing an RSVP and promotion from the waitlist, the journeys of ticket #8. */
class WithdrawalScenarios extends AcceptanceScenario {

    @Test
    void aWithdrawalPromotesTheEarliestWaitlistedPartiesThatFit() {
        String groupId = api.groupWith("alice", "bob", "carol", "dave");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(4).withGuestLimit(3));
        Api.assertConfirmed(api.rsvp("bob", eventId, 3), 4);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 2), 1);
        Api.assertWaitlisted(api.rsvp("dave", eventId, 0), 2);
        Api.assertWaitlisted(api.rsvp("alice", eventId, 0), 3);

        api.withdraw("bob", eventId).expectStatus().isNoContent();

        Api.assertConfirmed(api.myRsvp("carol", eventId), 3);
        Api.assertConfirmed(api.myRsvp("dave", eventId), 1);
        Api.assertWaitlisted(api.myRsvp("alice", eventId), 1);
        Api.assertRefused(api.myRsvp("bob", eventId), HttpStatus.NOT_FOUND, "rsvp-not-found");
        api.assertOpenSeats(eventId, 0);
    }

    @Test
    void aWithdrawalAfterTheCutoffIsAllowedButPromotesNobody() {
        String groupId = api.groupWith("alice", "bob", "carol");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK)
                        .withCapacity(1)
                        .withRsvpCutoff(NEXT_WEEK.minus(Duration.ofDays(1))));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 0), 1);

        clock.setInstant(NEXT_WEEK);
        api.withdraw("bob", eventId).expectStatus().isNoContent();

        Api.assertWaitlisted(api.myRsvp("carol", eventId), 1);
        api.assertOpenSeats(eventId, 1);
    }
}
