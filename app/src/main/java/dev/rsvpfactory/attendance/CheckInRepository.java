package dev.rsvpfactory.attendance;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CheckInRepository {

    private final JdbcClient jdbc;

    CheckInRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Records the count, replacing an earlier check-in of the same party. */
    CheckInView save(UUID eventId, UUID memberId, int arrived, Instant at) {
        return jdbc.sql("INSERT INTO check_in (event_id, member_id, arrived, checked_in_at)"
                        + " VALUES (:eventId, :memberId, :arrived, :at)"
                        + " ON CONFLICT (event_id, member_id)"
                        + " DO UPDATE SET arrived = EXCLUDED.arrived, checked_in_at = EXCLUDED.checked_in_at"
                        + " RETURNING member_id, arrived, checked_in_at")
                .param("eventId", eventId)
                .param("memberId", memberId)
                .param("arrived", arrived)
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .query(CheckInRepository::view)
                .single();
    }

    Optional<CheckInView> find(UUID eventId, UUID memberId) {
        return jdbc.sql("SELECT member_id, arrived, checked_in_at FROM check_in"
                        + " WHERE event_id = :eventId AND member_id = :memberId")
                .param("eventId", eventId)
                .param("memberId", memberId)
                .query(CheckInRepository::view)
                .optional();
    }

    void delete(UUID eventId, UUID memberId) {
        jdbc.sql("DELETE FROM check_in WHERE event_id = :eventId AND member_id = :memberId")
                .param("eventId", eventId)
                .param("memberId", memberId)
                .update();
    }

    /** Lowers the member's count to at most {@code max}; a lower count stays as it is. */
    void capAt(UUID eventId, UUID memberId, int max) {
        jdbc.sql("UPDATE check_in SET arrived = :max"
                        + " WHERE event_id = :eventId AND member_id = :memberId AND arrived > :max")
                .param("eventId", eventId)
                .param("memberId", memberId)
                .param("max", max)
                .update();
    }

    private static CheckInView view(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new CheckInView(
                rs.getObject("member_id", UUID.class),
                rs.getInt("arrived"),
                rs.getObject("checked_in_at", OffsetDateTime.class).toInstant());
    }
}
