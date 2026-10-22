package dev.rsvpfactory.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Changing the guest count on an RSVP, the journeys of ticket #9. */
class GuestChangeScenarios extends AcceptanceScenario {

    @Test
    void removingGuestsFromAFullEventPromotesTheWaitlistButAddingThemNeedsOpenSeats() {
        String groupId = api.groupWith("alice", "bob", "carol", "dave");
        String eventId = api.scheduleEvent(
                "alice",
                groupId,
                Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(4).withGuestLimit(3));
        Api.assertConfirmed(api.rsvp("bob", eventId, 3), 4);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 1), 1);
        Api.assertWaitlisted(api.rsvp("dave", eventId, 0), 2);

        Api.assertWaitlisted(api.rsvp("carol", eventId, 2), 1);
        Api.assertConfirmed(api.rsvp("bob", eventId, 1), 2);

        Api.assertWaitlisted(api.myRsvp("carol", eventId), 1);
        Api.assertConfirmed(api.myRsvp("dave", eventId), 1);
        api.assertOpenSeats(eventId, 1);
        Api.assertRefused(api.rsvp("bob", eventId, 3), HttpStatus.CONFLICT, "not-enough-open-seats");
        Api.assertConfirmed(api.myRsvp("bob", eventId), 2);
    }
}
