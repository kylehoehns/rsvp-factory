package dev.rsvpfactory.acceptance;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Check-ins follow RSVP changes, the journeys of ticket #83. */
class CheckInFollowsRsvpScenarios extends AcceptanceScenario {

    @Test
    void aMemberWhoWithdrawsAfterCheckInIsNoLongerCheckedIn() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withGuestLimit(2));
        Api.assertConfirmed(api.rsvp("bob", eventId, 2), 3);
        String bobId = api.memberId("bob");

        clock.setInstant(NEXT_WEEK.minus(Duration.ofMinutes(30)));
        api.put("alice", "/events/" + eventId + "/check-ins/" + bobId, Map.of())
                .expectStatus()
                .isOk();
        api.get("bob", "/events/" + eventId + "/check-ins/me").expectStatus().isOk();

        api.withdraw("bob", eventId).expectStatus().isNoContent();

        Api.assertRefused(
                api.get("bob", "/events/" + eventId + "/check-ins/me"), HttpStatus.NOT_FOUND, "check-in-not-found");
    }
}
