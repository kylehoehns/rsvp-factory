package dev.rsvpfactory.rsvp;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RsvpRepository {

    /** The seats confirmed parties hold at an event and how many RSVPs wait. */
    record Seats(int confirmedSeats, int waitlistLength) {

        static final Seats NONE = new Seats(0, 0);
    }

    private final JdbcClient jdbc;

    RsvpRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The event's confirmed parties and its waitlist in arrival order. */
    Seating.State state(long eventId) {
        List<Party> confirmed = new ArrayList<>();
        List<Party> waitlist = new ArrayList<>();
        jdbc.sql("SELECT member_id, party_size, status FROM rsvp WHERE event_id = :eventId ORDER BY created_at, id")
                .param("eventId", eventId)
                .query((rs, row) -> {
                    Party party = new Party(rs.getLong("member_id"), rs.getInt("party_size"));
                    (Placement.CONFIRMED.name().equalsIgnoreCase(rs.getString("status")) ? confirmed : waitlist)
                            .add(party);
                    return party;
                })
                .list();
        return new Seating.State(confirmed, waitlist);
    }

    void insert(long eventId, Party party, Placement placement, Instant createdAt) {
        jdbc.sql("INSERT INTO rsvp (event_id, member_id, party_size, status, created_at)"
                        + " VALUES (:eventId, :memberId, :partySize, :status, :createdAt)")
                .param("eventId", eventId)
                .param("memberId", party.memberId())
                .param("partySize", party.size())
                .param("status", placement.name().toLowerCase(Locale.ROOT))
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }

    void changePartySize(long eventId, long memberId, int partySize) {
        jdbc.sql("UPDATE rsvp SET party_size = :partySize WHERE event_id = :eventId AND member_id = :memberId")
                .param("partySize", partySize)
                .param("eventId", eventId)
                .param("memberId", memberId)
                .update();
    }

    /** Ends every RSVP to the event, confirmed or waitlisted. */
    void deleteAll(long eventId) {
        jdbc.sql("DELETE FROM rsvp WHERE event_id = :eventId")
                .param("eventId", eventId)
                .update();
    }

    void delete(long eventId, long memberId) {
        jdbc.sql("DELETE FROM rsvp WHERE event_id = :eventId AND member_id = :memberId")
                .param("eventId", eventId)
                .param("memberId", memberId)
                .update();
    }

    void confirm(long eventId, long memberId) {
        jdbc.sql("UPDATE rsvp SET status = :status WHERE event_id = :eventId AND member_id = :memberId")
                .param("status", Placement.CONFIRMED.name().toLowerCase(Locale.ROOT))
                .param("eventId", eventId)
                .param("memberId", memberId)
                .update();
    }

    Map<Long, Seats> seats(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("SELECT event_id,"
                        + " COALESCE(SUM(party_size) FILTER (WHERE status = 'confirmed'), 0) AS confirmed_seats,"
                        + " COUNT(*) FILTER (WHERE status = 'waitlisted') AS waitlist_length"
                        + " FROM rsvp WHERE event_id IN (:ids) GROUP BY event_id")
                .param("ids", eventIds)
                .query((rs, row) -> Map.entry(
                        rs.getLong("event_id"), new Seats(rs.getInt("confirmed_seats"), rs.getInt("waitlist_length"))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
