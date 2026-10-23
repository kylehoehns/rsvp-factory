package dev.rsvpfactory.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EventRepository {

    private final JdbcClient jdbc;

    EventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID insert(UUID groupId, String title, String description, String venue, Instant startsAt, Instant endsAt) {
        return jdbc.sql("INSERT INTO event (group_id, title, description, venue, starts_at, ends_at, status)"
                        + " VALUES (:groupId, :title, :description, :venue, :startsAt, :endsAt, :status)"
                        + " RETURNING id")
                .param("groupId", groupId)
                .param("title", title)
                .param("description", description)
                .param("venue", venue)
                .param("startsAt", java.sql.Timestamp.from(startsAt))
                .param("endsAt", java.sql.Timestamp.from(endsAt))
                .param("status", EventStatus.SCHEDULED.name())
                .query(UUID.class)
                .single();
    }

    /** Marks the event cancelled; false when it already was or does not exist. */
    boolean cancel(UUID id) {
        return jdbc.sql("UPDATE event SET status = :cancelled WHERE id = :id AND status <> :cancelled")
                        .param("cancelled", EventStatus.CANCELLED.name())
                        .param("id", id)
                        .update()
                > 0;
    }

    Optional<EventDetails> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(EventRepository::map)
                .optional();
    }

    List<EventDetails> inGroup(UUID groupId) {
        return jdbc.sql(SELECT + " WHERE group_id = :groupId ORDER BY starts_at, id")
                .param("groupId", groupId)
                .query(EventRepository::map)
                .list();
    }

    private static final String SELECT =
            "SELECT id, group_id, title, description, venue, starts_at, ends_at, status FROM event";

    private static EventDetails map(ResultSet rs, int row) throws SQLException {
        return new EventDetails(
                rs.getObject("id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("venue"),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getTimestamp("ends_at").toInstant(),
                EventStatus.valueOf(rs.getString("status")));
    }
}
