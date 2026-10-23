package dev.rsvpfactory.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Member ids are UUIDs that can't be guessed: the API shows them as UUID strings and refuses a made-up one. */
class MemberIdScenarios extends AcceptanceScenario {

    @Test
    void aMemberIdIsAUuidAndAnOrganizerIsMadeByIt() {
        String groupId = api.groupWith("alice", "bob");
        String bobId = api.memberId("bob");

        assertThat(UUID.fromString(bobId)).hasToString(bobId);
        api.post("alice", "/groups/" + groupId + "/organizers", Map.of("memberId", bobId))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.organizers[1].id")
                .isEqualTo(bobId);
    }

    @Test
    void aMadeUpOrMalformedMemberIdIsRefusedLikeAnyMemberWhoDoesNotExist() {
        String groupId = api.groupWith("alice", "bob");

        Api.assertRefused(
                api.post("alice", "/groups/" + groupId + "/organizers", Map.of("memberId", UUID.randomUUID())),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "not-a-member");
        Api.assertRefused(
                api.post("alice", "/groups/" + groupId + "/organizers", Map.of("memberId", "2")),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "not-a-member");
    }
}
