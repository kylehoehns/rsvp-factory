package dev.rsvpfactory.acceptance;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Changing an event's capacity, the journeys of ticket #10. */
class CapacityScenarios extends AcceptanceScenario {

    @Test
    void raisingTheCapacityPromotesTheWaitlist() {
        String groupId = api.groupWith("alice", "bob", "carol", "dave");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(2).withGuestLimit(1));
        Api.assertConfirmed(api.rsvp("bob", eventId, 1), 2);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 1), 1);
        Api.assertWaitlisted(api.rsvp("dave", eventId, 0), 2);

        api.put("alice", "/events/" + eventId + "/capacity", Map.of("capacity", 5))
                .expectStatus()
                .isOk();

        Api.assertConfirmed(api.myRsvp("carol", eventId), 2);
        Api.assertConfirmed(api.myRsvp("dave", eventId), 1);
        api.assertOpenSeats(eventId, 0);
    }

    @Test
    void capacityCannotGoBelowTheConfirmedSeatsAndOnlyOrganizersChangeIt() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(5).withGuestLimit(2));
        Api.assertConfirmed(api.rsvp("bob", eventId, 2), 3);

        Api.assertRefused(
                api.put("alice", "/events/" + eventId + "/capacity", Map.of("capacity", 2)),
                HttpStatus.CONFLICT,
                "capacity-below-confirmed-seats");
        Api.assertRefused(
                api.put("bob", "/events/" + eventId + "/capacity", Map.of("capacity", 9)),
                HttpStatus.FORBIDDEN,
                "not-an-organizer");
        api.put("alice", "/events/" + eventId + "/capacity", Map.of("capacity", 3))
                .expectStatus()
                .isOk();
        api.assertOpenSeats(eventId, 0);
    }
}
