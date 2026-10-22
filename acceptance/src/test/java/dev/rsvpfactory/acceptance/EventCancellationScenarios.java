package dev.rsvpfactory.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Cancelling an event, the journey of ticket #11. */
class EventCancellationScenarios extends AcceptanceScenario {

    @Test
    void anOrganizerCancelsAFullEventAndEveryRsvpEndsWithNoPromotion() {
        String groupId = api.groupWith("alice", "bob", "carol", "dave");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(1));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 0), 1);

        Api.assertRefused(
                api.post("bob", "/events/" + eventId + "/cancellation", java.util.Map.of()),
                HttpStatus.FORBIDDEN,
                "not-an-organizer");
        api.post("alice", "/events/" + eventId + "/cancellation", java.util.Map.of())
                .expectStatus()
                .is2xxSuccessful();

        api.event(eventId)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("cancelled")
                .jsonPath("$.openSeats")
                .isEqualTo(1)
                .jsonPath("$.waitlistLength")
                .isEqualTo(0);
        api.myRsvp("bob", eventId).expectStatus().isNotFound();
        api.myRsvp("carol", eventId).expectStatus().isNotFound();
        Api.assertRefused(api.rsvp("dave", eventId, 0), HttpStatus.CONFLICT, "event-cancelled");
    }
}
