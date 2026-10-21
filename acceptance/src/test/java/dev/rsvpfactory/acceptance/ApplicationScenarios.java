package dev.rsvpfactory.acceptance;

import org.junit.jupiter.api.Test;

/**
 * The example every new scenario class copies: extend {@link AcceptanceScenario}, act through {@link Api}, assert on
 * what a client of the HTTP API would see. Each ticket adds a class like this for the behaviour it delivers.
 */
class ApplicationScenarios extends AcceptanceScenario {

    @Test
    void theApplicationReportsHealthyWithoutSigningIn() {
        api.get(null, "/actuator/health")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }
}
