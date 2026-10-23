package dev.rsvpfactory.acceptance;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Checking a confirmed party in at the door, the journeys of ticket #80. */
class CheckInScenarios extends AcceptanceScenario {

    @Test
    void anOrganizerChecksAPartyInAtTheDoorAndTheMemberSeesIt() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withGuestLimit(2));
        Api.assertConfirmed(api.rsvp("bob", eventId, 2), 3);
        String bobId = api.memberId("bob");

        clock.setInstant(NEXT_WEEK.minus(Duration.ofMinutes(30)));
        api.put("alice", "/events/" + eventId + "/check-ins/" + bobId, Map.of("arrived", 2))
                .expectStatus()
                .isOk();
        api.put("alice", "/events/" + eventId + "/check-ins/" + bobId, Map.of())
                .expectStatus()
                .isOk();

        api.get("bob", "/events/" + eventId + "/check-ins/me")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.arrived")
                .isEqualTo(3);
    }

    @Test
    void aPartyCannotBeCheckedInBeforeTheDoorOpens() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent("alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        String bobId = api.memberId("bob");

        Api.assertRefused(
                api.put("alice", "/events/" + eventId + "/check-ins/" + bobId, Map.of()),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "check-in-closed");
        Api.assertRefused(
                api.get("bob", "/events/" + eventId + "/check-ins/me"), HttpStatus.NOT_FOUND, "check-in-not-found");
    }
}
