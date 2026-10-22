package dev.rsvpfactory.acceptance;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Signing in as a seeded demo member, starting a group, and viewing it. */
class SignInAndStartGroupScenarios extends AcceptanceScenario {

    @Test
    void aSignedInMemberStartsAGroupAndBecomesItsOrganizer() {
        String groupId = api.startGroup("alice");

        api.get(null, "/groups/" + groupId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(groupId)
                .jsonPath("$.name")
                .isEqualTo("Group of alice")
                .jsonPath("$.description")
                .isEqualTo("A test group")
                .jsonPath("$.organizers.length()")
                .isEqualTo(1)
                .jsonPath("$.organizers[0].name")
                .isEqualTo("Alice");
    }

    @Test
    void aGroupCanBeViewedWithoutSigningIn() {
        String groupId = api.startGroup("bob");

        api.get(null, "/groups/" + groupId).expectStatus().isOk();
    }

    @Test
    void groupsStartedByDifferentMembersHaveTheirOwnOrganizers() {
        String bobsGroup = api.startGroup("bob");
        api.startGroup("carol");

        api.get(null, "/groups/" + bobsGroup)
                .expectBody()
                .jsonPath("$.organizers.length()")
                .isEqualTo(1)
                .jsonPath("$.organizers[0].name")
                .isEqualTo("Bob");
    }

    @Test
    void startingAGroupWithoutSigningInIsRefusedAsUnauthenticated() {
        Api.assertRefused(
                api.post(null, "/groups", Map.of("name", "Nope", "description", "No sign-in")),
                HttpStatus.UNAUTHORIZED,
                "unauthenticated");
    }

    @Test
    void aWrongPasswordIsRefusedAsUnauthenticated() {
        Api.assertRefused(
                api.postWithPassword("alice", "wrong", "/groups", Map.of("name", "Nope", "description", "Bad")),
                HttpStatus.UNAUTHORIZED,
                "unauthenticated");
    }

    @Test
    void aBlankGroupNameIsRefusedAsAValidationFailure() {
        Api.assertRefused(
                api.post("alice", "/groups", Map.of("name", "  ", "description", "Blank name")),
                HttpStatus.BAD_REQUEST,
                "validation-failed");
    }

    @Test
    void aGroupThatDoesNotExistIsRefusedAsNotFound() {
        Api.assertRefused(api.get(null, "/groups/999999"), HttpStatus.NOT_FOUND, "group-not-found");
    }

    @Test
    void aGroupStartedWithoutADescriptionHasAnEmptyOne() {
        api.post("dave", "/groups", Map.of("name", "Quiet group"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Quiet group")
                .jsonPath("$.description")
                .isEqualTo("")
                .jsonPath("$.organizers[0].name")
                .isEqualTo("Dave");
    }
}
