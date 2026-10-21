package dev.rsvpfactory.acceptance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The only two things the suite controls inside the app: which database it talks to, and what time it is. The app must
 * take time from a {@link java.time.Clock} bean for the cutoff scenarios to work.
 */
@TestConfiguration(proxyBeanMethods = false)
class AcceptanceConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }

    @Bean
    @Primary
    MutableClock acceptanceClock() {
        return new MutableClock(AcceptanceScenario.NOW);
    }
}
