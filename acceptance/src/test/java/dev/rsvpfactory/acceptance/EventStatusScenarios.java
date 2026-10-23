package dev.rsvpfactory.acceptance;

import org.junit.jupiter.api.Test;

/** An event keeps a readable status through its life, the journey of ticket #40. */
class EventStatusScenarios extends AcceptanceScenario {

    @Test
    void aScheduledThenCancelledEventStaysInThePublicList() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent("alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK));
        api.get(null, "/groups/" + groupId + "/events")
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo("scheduled");

        api.post("alice", "/events/" + eventId + "/cancellation", java.util.Map.of())
                .expectStatus()
                .is2xxSuccessful();

        api.get(null, "/groups/" + groupId + "/events")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(eventId)
                .jsonPath("$[0].status")
                .isEqualTo("cancelled");
    }
}
