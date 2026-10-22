package dev.rsvpfactory.acceptance;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The waitlist follows the order RSVPs were made in, the journey of ticket #45. */
class WaitlistOrderScenarios extends AcceptanceScenario {

    @Test
    void waitlistedPartiesArePromotedInTheOrderTheyRsvpedAcrossTime() {
        String groupId = api.groupWith("alice", "bob", "carol", "dave");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(1));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        Api.assertWaitlisted(api.rsvp("carol", eventId, 0), 1);
        clock.setInstant(NOW.plus(Duration.ofHours(1)));
        Api.assertWaitlisted(api.rsvp("dave", eventId, 0), 2);

        api.withdraw("bob", eventId).expectStatus().isNoContent();

        Api.assertConfirmed(api.myRsvp("carol", eventId), 1);
        Api.assertWaitlisted(api.myRsvp("dave", eventId), 1);
    }
}
