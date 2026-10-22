package dev.rsvpfactory.acceptance;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Leaving a group withdraws upcoming RSVPs, the journeys of ticket #12. */
class LeavingGroupScenarios extends AcceptanceScenario {

    @Test
    void leavingAGroupWithdrawsUpcomingRsvpsAndPromotesTheWaitlist() {
        String groupId = api.groupWith("alice", "bob", "carol");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(1));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 0), 1);

        api.leave("bob", groupId);

        Api.assertRefused(api.myRsvp("bob", eventId), HttpStatus.NOT_FOUND, "rsvp-not-found");
        Api.assertConfirmed(api.myRsvp("carol", eventId), 1);
        api.assertOpenSeats(eventId, 0);
    }

    @Test
    void leavingAGroupKeepsRsvpsToEventsThatHaveStarted() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(2));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);

        clock.setInstant(NEXT_WEEK.plus(Duration.ofHours(1)));
        api.leave("bob", groupId);

        Api.assertConfirmed(api.myRsvp("bob", eventId), 1);
        api.assertOpenSeats(eventId, 1);
    }
}
