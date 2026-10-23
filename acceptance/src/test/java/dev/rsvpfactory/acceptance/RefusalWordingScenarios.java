package dev.rsvpfactory.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Each refusal is built in one place, the journey of ticket #60. */
class RefusalWordingScenarios extends AcceptanceScenario {

    @Test
    void withdrawingAnRsvpTheMemberDoesNotHaveIsNotFound() {
        String groupId = api.groupWith("alice", "bob");
        String eventId = api.scheduleEvent(
                "alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK).withCapacity(2));

        Api.assertRefused(api.withdraw("bob", eventId), HttpStatus.NOT_FOUND, "rsvp-not-found");
    }
}
