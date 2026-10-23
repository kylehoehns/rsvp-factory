package dev.rsvpfactory.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rsvpfactory.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What the event table guarantees: only known statuses, and an end after the start. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventTableTests {

    @Autowired
    JdbcClient jdbc;

    private int insert(String status, String startsAt, String endsAt) {
        return jdbc.sql(
                        "INSERT INTO event (group_id, title, description, venue, starts_at, ends_at, status)"
                                + " VALUES ('0197a001-0000-7000-8000-000000000001', 't', 'd', 'v', :startsAt::timestamptz, :endsAt::timestamptz, :status)")
                .param("startsAt", startsAt)
                .param("endsAt", endsAt)
                .param("status", status)
                .update();
    }

    @Test
    void theDatabaseAcceptsEveryEventStatusWithAnEndAfterTheStart() {
        for (EventStatus status : EventStatus.values()) {
            assertThat(insert(status.name(), "2030-03-01T18:00:00Z", "2030-03-01T20:00:00Z"))
                    .isEqualTo(1);
        }
    }

    @Test
    void theDatabaseRefusesAnUnknownStatus() {
        assertThatThrownBy(() -> insert("POSTPONED", "2030-03-01T18:00:00Z", "2030-03-01T20:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("event_status_known");
    }

    @Test
    void theDatabaseRefusesAnEventThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> insert("SCHEDULED", "2030-03-01T20:00:00Z", "2030-03-01T18:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("event_ends_after_start");
    }

    @Test
    void theDatabaseRefusesAnEventThatEndsWhenItStarts() {
        assertThatThrownBy(() -> insert("SCHEDULED", "2030-03-01T18:00:00Z", "2030-03-01T18:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("event_ends_after_start");
    }
}
