package dev.rsvpfactory.acceptance;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Undoing a check-in made by mistake, the journey of ticket #81. */
class UndoCheckInScenarios extends AcceptanceScenario {

    @Test
    void anOrganizerChecksTheWrongMemberInAndUndoesIt() {
        String groupId = api.groupWith("alice", "bob", "carol");
        String eventId = api.scheduleEvent("alice", groupId, Api.EventRequest.startingAt(NEXT_WEEK));
        Api.assertConfirmed(api.rsvp("bob", eventId, 0), 1);
        Api.assertConfirmed(api.rsvp("carol", eventId, 0), 1);
        String bobId = api.memberId("bob");
        String carolId = api.memberId("carol");

        clock.setInstant(NEXT_WEEK.minus(Duration.ofMinutes(10)));
        api.put("alice", "/events/" + eventId + "/check-ins/" + bobId, Map.of())
                .expectStatus()
                .isOk();

        api.delete("alice", "/events/" + eventId + "/check-ins/" + bobId)
                .expectStatus()
                .isNoContent();
        Api.assertRefused(
                api.get("bob", "/events/" + eventId + "/check-ins/me"), HttpStatus.NOT_FOUND, "check-in-not-found");

        api.put("alice", "/events/" + eventId + "/check-ins/" + carolId, Map.of())
                .expectStatus()
                .isOk();
        api.get("carol", "/events/" + eventId + "/check-ins/me").expectStatus().isOk();
    }
}
