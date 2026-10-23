package dev.rsvpfactory.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Group ids are UUIDs that can't be guessed: the API shows them as UUID strings and refuses a made-up one. */
class GroupIdScenarios extends AcceptanceScenario {

    @Test
    void aGroupIdIsAUuidAndItsLinkKeepsWorking() {
        String groupId = api.startGroup("alice");

        assertThat(UUID.fromString(groupId)).hasToString(groupId);
        api.get(null, "/groups/" + groupId).expectStatus().isOk();
        api.get(null, "/groups/" + groupId + "/events").expectStatus().isOk();
    }

    @Test
    void groupsCreatedOneAfterAnotherDoNotPredictEachOther() {
        String first = api.startGroup("alice");
        String second = api.startGroup("alice");

        assertThat(second).isNotEqualTo(first);
        // The next id is not the previous one plus one, as a sequential number would be.
        assertThat(UUID.fromString(second).getLeastSignificantBits())
                .isNotEqualTo(UUID.fromString(first).getLeastSignificantBits() + 1);
    }

    @Test
    void aMadeUpOrMalformedGroupIdIsNotFound() {
        Api.assertRefused(api.get(null, "/groups/" + UUID.randomUUID()), HttpStatus.NOT_FOUND, "group-not-found");
        Api.assertRefused(api.get(null, "/groups/1"), HttpStatus.NOT_FOUND, "group-not-found");
        Api.assertRefused(
                api.get(null, "/groups/" + UUID.randomUUID() + "/events"), HttpStatus.NOT_FOUND, "group-not-found");
        Api.assertRefused(api.get(null, "/groups/nonsense/events"), HttpStatus.NOT_FOUND, "group-not-found");
    }
}
