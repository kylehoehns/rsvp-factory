package dev.rsvpfactory.acceptance;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Joining and leaving a group, and sharing the organizer role. */
class MembershipScenarios extends AcceptanceScenario {

    @Test
    void membersJoinTwiceHarmlesslyThenLeaveAndTheCountFollows() {
        String groupId = api.startGroup("alice");

        api.join("bob", groupId);
        api.join("bob", groupId);
        api.get(null, "/groups/" + groupId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.memberCount")
                .isEqualTo(2);

        api.leave("bob", groupId);
        api.get(null, "/groups/" + groupId)
                .expectBody()
                .jsonPath("$.memberCount")
                .isEqualTo(1);
    }

    @Test
    void anOrganizerMakesAMemberAnOrganizerButNobodyElseCan() {
        String groupId = api.groupWith("alice", "bob", "carol");

        Api.assertRefused(
                api.post("bob", "/groups/" + groupId + "/organizers", Map.of("memberId", api.memberId("carol"))),
                HttpStatus.FORBIDDEN,
                "not-an-organizer");
        Api.assertRefused(
                api.post("alice", "/groups/" + groupId + "/organizers", Map.of("memberId", api.memberId("dave"))),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "not-a-member");

        api.post("alice", "/groups/" + groupId + "/organizers", Map.of("memberId", api.memberId("bob")))
                .expectStatus()
                .isOk();
        api.get(null, "/groups/" + groupId)
                .expectBody()
                .jsonPath("$.organizers.length()")
                .isEqualTo(2);
    }
}
