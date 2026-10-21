package dev.rsvpfactory.acceptance;

import dev.rsvpfactory.RsvpFactoryApplication;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base for every scenario: the real application on a random port, a real Postgres, and a clock the scenario controls.
 * Each scenario creates its own groups and events, so scenarios don't depend on each other.
 */
@SpringBootTest(classes = RsvpFactoryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AcceptanceConfiguration.class)
abstract class AcceptanceScenario {

    static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    static final Instant NEXT_WEEK = NOW.plus(Duration.ofDays(7));

    @Value("${local.server.port}")
    int port;

    @Autowired
    MutableClock clock;

    Api api;

    @BeforeEach
    void setUpScenario() {
        clock.setInstant(NOW);
        api = new Api(port);
    }
}
