package dev.rsvpfactory.acceptance;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every seeded demo member can sign in with the demo password, whatever ids the database gave them. */
class DemoMemberScenarios extends AcceptanceScenario {

    @ParameterizedTest
    @ValueSource(strings = {"alice", "bob", "carol", "dave"})
    void aDemoMemberSignsInAndStartsAGroup(String member) {
        api.post(member, "/groups", Map.of("name", member + "'s club", "description", "Demo"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.organizers[0].username")
                .isEqualTo(member);
    }
}
