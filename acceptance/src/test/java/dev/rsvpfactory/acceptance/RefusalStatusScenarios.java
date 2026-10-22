package dev.rsvpfactory.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The status each seating refusal answers with, the journey of ticket #44. */
class RefusalStatusScenarios extends AcceptanceScenario {

    @Test
    void rsvpingAgainWithAnotherPartyIsAConflictAndKeepsTheFirstRsvp() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(10).withGuestLimit(2));

        Api.assertConfirmed(api.rsvp("bob", eventId, 1), 2);

        Api.assertRefused(api.rsvp("bob", eventId, 2), HttpStatus.CONFLICT, "rsvp-party-change-unsupported");
        api.assertOpenSeats(eventId, 8);
    }
}
