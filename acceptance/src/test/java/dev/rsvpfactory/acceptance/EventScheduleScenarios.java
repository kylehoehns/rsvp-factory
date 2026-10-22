package dev.rsvpfactory.acceptance;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Scheduling an event and seeing its open seats, the journeys of ticket #5. */
class EventScheduleScenarios extends AcceptanceScenario {

    @Test
    void anOrganizerSchedulesAnEventAndAVisitorSeesItInTheGroupWithItsOpenSeats() {
        String groupId = api.startGroup("alice");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(8));

        api.get(null, "/groups/" + groupId + "/events")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(eventId)
                .jsonPath("$[0].capacity")
                .isEqualTo(8)
                .jsonPath("$[0].openSeats")
                .isEqualTo(8)
                .jsonPath("$[0].waitlistLength")
                .isEqualTo(0)
                .jsonPath("$[0].status")
                .isEqualTo("scheduled");
        api.event(eventId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Monthly meetup")
                .jsonPath("$.venue")
                .isEqualTo("123 Main St")
                .jsonPath("$.guestLimit")
                .isEqualTo(0)
                .jsonPath("$.rsvpCutoff")
                .isEqualTo(NEXT_WEEK.toString());
    }

    @Test
    void onlyAnOrganizerSchedulesAndEveryBrokenRuleIsRefused() {
        String groupId = api.startGroup("alice");
        Api.EventRequest event = Api.EventRequest.startingAt(NEXT_WEEK);

        Api.assertRefused(
                api.post("bob", "/groups/" + groupId + "/events", event.toJson()),
                HttpStatus.FORBIDDEN,
                "not-an-organizer");
        Api.assertRefused(
                api.post(
                        "alice",
                        "/groups/" + groupId + "/events",
                        event.withCapacity(0).toJson()),
                HttpStatus.BAD_REQUEST,
                "capacity-not-positive");
        Api.assertRefused(
                api.post(
                        "alice",
                        "/groups/" + groupId + "/events",
                        event.endingAt(NEXT_WEEK).toJson()),
                HttpStatus.BAD_REQUEST,
                "end-not-after-start");
        Api.assertRefused(
                api.post(
                        "alice",
                        "/groups/" + groupId + "/events",
                        event.withRsvpCutoff(NEXT_WEEK.plus(Duration.ofHours(1)))
                                .toJson()),
                HttpStatus.BAD_REQUEST,
                "rsvp-cutoff-after-start");
    }
}
