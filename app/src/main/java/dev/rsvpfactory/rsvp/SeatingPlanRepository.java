package dev.rsvpfactory.rsvp;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SeatingPlanRepository {

    private final JdbcClient jdbc;

    SeatingPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(long eventId, SeatingPlan plan) {
        jdbc.sql("INSERT INTO seating_plan (event_id, capacity, guest_limit, rsvp_cutoff)"
                        + " VALUES (:eventId, :capacity, :guestLimit, :rsvpCutoff)")
                .param("eventId", eventId)
                .param("capacity", plan.capacity())
                .param("guestLimit", plan.guestLimit())
                .param("rsvpCutoff", Timestamp.from(plan.rsvpCutoff()))
                .update();
    }

    /** Takes the event's seating plan with a row lock, so RSVPs to one event are seated one at a time. */
    Optional<SeatingPlan> lock(long eventId) {
        return jdbc.sql(
                        "SELECT capacity, guest_limit, rsvp_cutoff FROM seating_plan WHERE event_id = :eventId FOR UPDATE")
                .param("eventId", eventId)
                .query((rs, row) -> new SeatingPlan(
                        rs.getInt("capacity"),
                        rs.getInt("guest_limit"),
                        rs.getTimestamp("rsvp_cutoff").toInstant()))
                .optional();
    }

    Optional<SeatingPlan> find(long eventId) {
        return Optional.ofNullable(byEventIds(List.of(eventId)).get(eventId));
    }

    Map<Long, SeatingPlan> byEventIds(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("SELECT event_id, capacity, guest_limit, rsvp_cutoff FROM seating_plan"
                        + " WHERE event_id IN (:ids)")
                .param("ids", eventIds)
                .query((rs, row) -> Map.entry(
                        rs.getLong("event_id"),
                        new SeatingPlan(
                                rs.getInt("capacity"),
                                rs.getInt("guest_limit"),
                                rs.getTimestamp("rsvp_cutoff").toInstant())))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
