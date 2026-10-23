package dev.rsvpfactory.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What the rsvp table guarantees: only known statuses, and a time for every RSVP that orders the waitlist. */
@Import({TestcontainersConfiguration.class, RsvpTableTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RsvpTableTests {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class Setup {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcClient jdbc;

    private RsvpApi api() {
        return new RsvpApi(port);
    }

    @Test
    void theDatabaseRefusesAnUnknownStatus() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO rsvp (event_id, member_id, party_size, status, created_at)"
                                + " VALUES (1, '0197a000-0000-7000-8000-000000000001', 1, 'maybe', now())")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesAnRsvpWithoutATime() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO rsvp (event_id, member_id, party_size, status)"
                                + " VALUES (1, '0197a000-0000-7000-8000-000000000001', 1, 'confirmed')")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aNewRsvpIsMadeAtTheClocksTime() {
        String eventId = api().event(api().groupWith("alice", "bob"), 5, 0);

        api().rsvp("bob", eventId, 0).expectStatus().isOk();

        OffsetDateTime madeAt = jdbc.sql("SELECT created_at FROM rsvp WHERE event_id = :eventId")
                .param("eventId", Long.parseLong(eventId))
                .query(OffsetDateTime.class)
                .single();
        assertThat(madeAt.toInstant()).isEqualTo(NOW);
    }

    @Test
    void theWaitlistIsOrderedByWhenRsvpsWereMadeThenById() {
        String eventId = api().event(api().groupWith("alice", "bob", "carol", "dave"), 1, 0);
        api().rsvp("bob", eventId, 0).expectStatus().isOk();
        api().rsvp("carol", eventId, 0).expectStatus().isOk();
        api().rsvp("dave", eventId, 0).expectStatus().isOk();
        api().rsvp("alice", eventId, 0).expectStatus().isOk();
        // carol was made last by the clock's reckoning, dave and alice tie and fall back to id order
        jdbc.sql("UPDATE rsvp SET created_at = :at WHERE event_id = :eventId AND status = 'waitlisted'"
                        + " AND member_id = (SELECT id FROM member WHERE username = 'carol')")
                .param("at", OffsetDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC))
                .param("eventId", Long.parseLong(eventId))
                .update();

        api().mine("dave", eventId).expectBody().jsonPath("$.waitlistPosition").isEqualTo(1);
        api().mine("alice", eventId).expectBody().jsonPath("$.waitlistPosition").isEqualTo(2);
        api().mine("carol", eventId).expectBody().jsonPath("$.waitlistPosition").isEqualTo(3);
    }
}
