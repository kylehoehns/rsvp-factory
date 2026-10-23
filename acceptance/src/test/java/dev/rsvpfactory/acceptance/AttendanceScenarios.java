package dev.rsvpfactory.acceptance;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The organizer's attendance view, the journeys of ticket #82. */
class AttendanceScenarios extends AcceptanceScenario {

    @Test
    void anOrganizerSeesWhoIsStillMissing() {
        String groupId = api.groupWith("alice", "bob", "carol");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withGuestLimit(2));
        Api.assertConfirmed(api.rsvp("bob", eventId, 2), 3);
        Api.assertConfirmed(api.rsvp("carol", eventId, 0), 1);
        String bobId = api.memberId("bob");
        String carolId = api.memberId("carol");

        clock.setInstant(NEXT_WEEK);
        api.put("alice", "/events/" + eventId + "/check-ins/" + bobId, Map.of("arrived", 2))
                .expectStatus()
                .isOk();

        api.get("alice", "/events/" + eventId + "/attendance")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.seatsConfirmed")
                .isEqualTo(4)
                .jsonPath("$.peopleCheckedIn")
                .isEqualTo(2)
                .jsonPath("$.parties[?(@.memberId=='" + bobId + "')].arrived")
                .isEqualTo(2)
                .jsonPath("$.parties[?(@.memberId=='" + carolId + "')].partySize")
                .isEqualTo(1);
        api.get("alice", "/events/" + eventId + "/attendance")
                .expectBody()
                .jsonPath("$.parties[1].memberId")
                .isEqualTo(carolId)
                .jsonPath("$.parties[1].arrived")
                .isEmpty();
    }

    @Test
    void onlyAnOrganizerSeesAttendance() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent("alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK));
        clock.setInstant(NEXT_WEEK.plus(Duration.ofDays(1)));

        Api.assertRefused(
                api.get("bob", "/events/" + eventId + "/attendance"), HttpStatus.FORBIDDEN, "not-an-organizer");
        api.get("alice", "/events/" + eventId + "/attendance").expectStatus().isOk();
    }
}
