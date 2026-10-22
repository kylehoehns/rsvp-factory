package dev.rsvpfactory.acceptance;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The guest limit and the RSVP cutoff, the journeys of ticket #7. */
class RsvpLimitScenarios extends AcceptanceScenario {

    @Test
    void aPartyOverTheGuestLimitIsRefusedAndAPartyAtTheLimitIsConfirmed() {
        String groupId = api.groupWith("alice", "bob", "carol");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(10).withGuestLimit(2));

        Api.assertRefused(api.rsvp("bob", eventId, 3), HttpStatus.UNPROCESSABLE_CONTENT, "guest-limit-exceeded");
        api.assertOpenSeats(eventId, 10);

        Api.assertConfirmed(api.rsvp("bob", eventId, 2), 3);
        api.assertOpenSeats(eventId, 7);
    }

    @Test
    void anRsvpIsAcceptedUntilTheCutoffAndRefusedOnceItHasPassed() {
        String groupId = api.groupWith("alice", "bob", "carol");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(5).withRsvpCutoff(NOW.plus(Duration.ofDays(2))));

        clock.setInstant(NOW.plus(Duration.ofDays(2)).minusSeconds(1));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);

        clock.setInstant(NOW.plus(Duration.ofDays(3)));
        Api.assertRefused(api.rsvp("carol", eventId, 0), HttpStatus.CONFLICT, "rsvp-cutoff-passed");
        api.assertOpenSeats(eventId, 4);
    }
}
