package dev.rsvpfactory.acceptance;

import static dev.rsvpfactory.acceptance.Api.assertRefused;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Signing in and starting a group: the first tracer bullet through every layer. */
class StartingAGroupScenarios extends AcceptanceScenario {

    @Test
    void aSignedInMemberStartsAGroupAndBecomesItsOrganizer() {
        String groupId = api.startGroup("alice");

        api.get(null, "/groups/" + groupId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(Integer.parseInt(groupId))
                .jsonPath("$.name")
                .isEqualTo("Group of alice")
                .jsonPath("$.description")
                .isEqualTo("A test group")
                .jsonPath("$.organizers.length()")
                .isEqualTo(1)
                .jsonPath("$.organizers[0].name")
                .isEqualTo("alice");
    }

    @Test
    void anyoneCanViewAGroupWithoutSigningIn() {
        String groupId = api.startGroup("bob");

        api.get(null, "/groups/" + groupId).expectStatus().isOk();
    }

    @Test
    void viewingAGroupThatDoesNotExistIsRefused() {
        assertRefused(api.get(null, "/groups/999999"), HttpStatus.NOT_FOUND, "group-not-found");
    }

    @Test
    void startingAGroupWithoutSigningInIsRefusedAsUnauthenticated() {
        assertRefused(
                api.post(null, "/groups", Map.of("name", "Sneaky", "description", "No credentials")),
                HttpStatus.UNAUTHORIZED,
                "unauthenticated");
    }

    @Test
    void aWrongPasswordIsRefusedAsUnauthenticated() {
        assertRefused(
                api.postWithPassword("alice", "wrong", "/groups", Map.of("name", "Sneaky", "description", "")),
                HttpStatus.UNAUTHORIZED,
                "unauthenticated");
    }

    @Test
    void aBlankGroupNameIsRefusedAsAValidationError() {
        assertRefused(
                api.post("carol", "/groups", Map.of("name", "  ", "description", "Nameless")),
                HttpStatus.BAD_REQUEST,
                "validation-failed");
    }
}
