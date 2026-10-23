package dev.rsvpfactory.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Event ids are UUIDs that can't be guessed: the API shows them as UUID strings and refuses a made-up one. */
class EventIdScenarios extends AcceptanceScenario {

    @Test
    void anEventIdIsAUuidAndEveryLinkKeepsWorking() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(5).withGuestLimit(1));

        assertThat(UUID.fromString(eventId)).hasToString(eventId);
        assertThat(UUID.fromString(eventId).version()).isEqualTo(7);
        api.get(null, "/events/" + eventId).expectStatus().isOk();
        api.get(null, "/groups/" + groupId + "/events")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(eventId);
        Api.assertConfirmed(api.rsvp("bob", eventId, 1), 2);
        Api.assertConfirmed(api.myRsvp("bob", eventId), 2);
        api.put("alice", "/events/" + eventId + "/capacity", Map.of("capacity", 6))
                .expectStatus()
                .isOk();
        api.withdraw("bob", eventId).expectStatus().isNoContent();
        api.post("alice", "/events/" + eventId + "/cancellation", Map.of())
                .expectStatus()
                .isOk();
    }

    @Test
    void eventsScheduledOneAfterAnotherDoNotPredictEachOther() {
        String groupId = api.groupWith("alice");
        Api.EventRequest request =
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(5).withGuestLimit(1);
        String first = api.scheduleEvent("alice", groupId, request);
        String second = api.scheduleEvent("alice", groupId, request);

        assertThat(second).isNotEqualTo(first);
        assertThat(UUID.fromString(second).getLeastSignificantBits())
                .isNotEqualTo(UUID.fromString(first).getLeastSignificantBits() + 1);
    }

    @Test
    void aMadeUpOrMalformedEventIdIsNotFound() {
        api.groupWith("alice", "bob");
        for (String id : new String[] {UUID.randomUUID().toString(), "1", "nonsense"}) {
            String path = "/events/" + id;
            Api.assertRefused(api.get(null, path), HttpStatus.NOT_FOUND, "event-not-found");
            Api.assertRefused(api.rsvp("bob", id, 0), HttpStatus.NOT_FOUND, "event-not-found");
            Api.assertRefused(api.myRsvp("bob", id), HttpStatus.NOT_FOUND, "event-not-found");
            Api.assertRefused(api.withdraw("bob", id), HttpStatus.NOT_FOUND, "event-not-found");
            Api.assertRefused(
                    api.put("alice", path + "/capacity", Map.of("capacity", 3)),
                    HttpStatus.NOT_FOUND,
                    "event-not-found");
            Api.assertRefused(
                    api.post("alice", path + "/cancellation", Map.of()), HttpStatus.NOT_FOUND, "event-not-found");
        }
    }
}
