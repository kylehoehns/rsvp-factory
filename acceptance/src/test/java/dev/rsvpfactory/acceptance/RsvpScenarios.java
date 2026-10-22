package dev.rsvpfactory.acceptance;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** RSVPing to an event, the journeys of ticket #6. */
class RsvpScenarios extends AcceptanceScenario {

    @Test
    void membersFillAnEventAndTheNextPartyJoinsTheWaitlist() {
        String groupId = api.groupWith("alice", "bob", "carol", "dave");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(4).withGuestLimit(2));

        Api.assertConfirmed(api.rsvp("bob", eventId, 2), 3);
        api.assertOpenSeats(eventId, 1);

        Api.assertWaitlisted(api.rsvp("carol", eventId, 1), 1);
        api.assertOpenSeats(eventId, 1);
        Api.assertConfirmed(api.rsvp("dave", eventId, 0), 1);
        api.assertOpenSeats(eventId, 0);

        Api.assertConfirmed(api.myRsvp("bob", eventId), 3);
        Api.assertWaitlisted(api.myRsvp("carol", eventId), 1);
        api.event(eventId).expectBody().jsonPath("$.waitlistLength").isEqualTo(1);
    }

    @Test
    void rsvpingTwiceHoldsOneSeatAndAnOutsiderOrALateMemberIsRefused() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK)
                        .withCapacity(3)
                        .withRsvpCutoff(NEXT_WEEK.minus(Duration.ofDays(1))));

        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        api.assertOpenSeats(eventId, 2);

        Api.assertRefused(api.rsvp("carol", eventId, 0), HttpStatus.FORBIDDEN, "not-a-member");

        clock.setInstant(NEXT_WEEK);
        api.join("carol", groupId);
        Api.assertRefused(api.rsvp("carol", eventId, 0), HttpStatus.CONFLICT, "rsvp-cutoff-passed");
    }
}
