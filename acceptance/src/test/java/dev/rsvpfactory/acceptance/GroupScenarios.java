package dev.rsvpfactory.acceptance;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Signing in and starting a group, the first journeys through every layer. */
class GroupScenarios extends AcceptanceScenario {

    @Test
    void aMemberStartsAGroupAndAVisitorSeesItWithItsOrganizer() {
        String groupId = api.startGroup("alice");

        api.get(null, "/groups/" + groupId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Group of alice")
                .jsonPath("$.description")
                .isEqualTo("A test group")
                .jsonPath("$.organizers[0].username")
                .isEqualTo("alice");
    }

    @Test
    void aVisitorCannotStartAGroupAndABlankNameIsRefused() {
        Api.assertRefused(
                api.post(null, "/groups", Map.of("name", "Chess club")), HttpStatus.UNAUTHORIZED, "not-authenticated");
        Api.assertRefused(api.post("bob", "/groups", Map.of("name", " ")), HttpStatus.BAD_REQUEST, "validation-failed");
        Api.assertRefused(
                api.postWithPassword("bob", "wrong", "/groups", Map.of("name", "Chess club")),
                HttpStatus.UNAUTHORIZED,
                "not-authenticated");
    }
}
