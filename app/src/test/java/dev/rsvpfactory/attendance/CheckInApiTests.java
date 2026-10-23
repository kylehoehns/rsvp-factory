package dev.rsvpfactory.attendance;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** The HTTP API seam for checking a confirmed party in, as the seeded demo members, with a clock the test moves. */
@Import({TestcontainersConfiguration.class, CheckInApiTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckInApiTests {

    private static final String PASSWORD = "rsvp-demo";
    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final Instant STARTS = Instant.parse("2030-04-01T18:00:00Z");
    private static final Instant ENDS = Instant.parse("2030-04-01T20:00:00Z");
    private static final String UNKNOWN = "0197a002-0000-7000-8000-00000000ffff";

    static class MovableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Setup {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    MovableClock clock;

    private String groupId;
    private String eventId;
    private String bobId;

    @BeforeEach
    void bobBringsTwoGuests() {
        clock.set(NOW);
        groupId = id(post("alice", "/groups", Map.of("name", "Chess club")));
        bobId = organizerId("bob");
        put("bob", "/groups/" + groupId + "/members/me", Map.of())
                .expectStatus()
                .is2xxSuccessful();
        put("carol", "/groups/" + groupId + "/members/me", Map.of())
                .expectStatus()
                .is2xxSuccessful();
        eventId = id(post("alice", "/groups/" + groupId + "/events", event(2)));
        put("bob", "/events/" + eventId + "/rsvps/me", Map.of("guests", 2))
                .expectStatus()
                .isOk();
    }

    @Test
    void checksInTheWholePartyByDefaultFromAnHourBeforeTheStart() {
        clock.set(STARTS.minus(Duration.ofHours(1)));

        checkIn("alice", bobId, null)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.arrived")
                .isEqualTo(3);
        mine("bob").expectStatus().isOk().expectBody().jsonPath("$.arrived").isEqualTo(3);
    }

    @Test
    void checkingInAgainSetsTheCount() {
        clock.set(STARTS);
        checkIn("alice", bobId, 1)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.arrived")
                .isEqualTo(1);
        checkIn("alice", bobId, 2)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.arrived")
                .isEqualTo(2);
        checkIn("alice", bobId, 2).expectStatus().isOk();
        mine("bob").expectBody().jsonPath("$.arrived").isEqualTo(2);
        checkIn("alice", bobId, null).expectBody().jsonPath("$.arrived").isEqualTo(3);
    }

    @Test
    void refusesANonOrganizer() {
        clock.set(STARTS);
        assertRefused(checkIn("carol", bobId, null), 403, "not-an-organizer");
        assertRefused(checkIn("bob", bobId, null), 403, "not-an-organizer");
    }

    @Test
    void refusesAMemberWithoutAConfirmedRsvp() {
        String carolId = organizerId("carol");
        clock.set(STARTS);
        assertRefused(checkIn("alice", carolId, null), 422, "rsvp-not-confirmed");
        clock.set(NOW);
        put("carol", "/events/" + eventId + "/rsvps/me", Map.of("guests", 0))
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("waitlisted");
        clock.set(STARTS);
        assertRefused(checkIn("alice", carolId, null), 422, "rsvp-not-confirmed");
    }

    @Test
    void refusesOutsideTheWindow() {
        clock.set(STARTS.minus(Duration.ofHours(1)).minusSeconds(1));
        assertRefused(checkIn("alice", bobId, null), 422, "check-in-closed");
        clock.set(ENDS);
        assertRefused(checkIn("alice", bobId, null), 422, "check-in-closed");
        clock.set(ENDS.minusSeconds(1));
        checkIn("alice", bobId, null).expectStatus().isOk();
    }

    @Test
    void refusesACancelledEvent() {
        post("alice", "/events/" + eventId + "/cancellation", Map.of())
                .expectStatus()
                .isOk();
        clock.set(STARTS);
        assertRefused(checkIn("alice", bobId, null), 422, "check-in-closed");
    }

    @Test
    void refusesACountAboveThePartyOrBelowOne() {
        clock.set(STARTS);
        assertRefused(checkIn("alice", bobId, 4), 422, "arrived-exceeds-party");
        checkIn("alice", bobId, 3).expectStatus().isOk();
        assertRefused(checkIn("alice", bobId, 0), 422, "arrived-must-be-positive");
        assertRefused(checkIn("alice", bobId, -1), 422, "arrived-must-be-positive");
        mine("bob").expectBody().jsonPath("$.arrived").isEqualTo(3);
    }

    @Test
    void unknownOrMalformedIdsAreNotFound() {
        clock.set(STARTS);
        assertRefused(put("alice", "/events/" + UNKNOWN + "/check-ins/" + bobId, Map.of()), 404, "event-not-found");
        assertRefused(put("alice", "/events/nope/check-ins/" + bobId, Map.of()), 404, "event-not-found");
        assertRefused(put("alice", "/events/" + eventId + "/check-ins/nope", Map.of()), 404, "member-not-found");
        assertRefused(get("bob", "/events/nope/check-ins/me"), 404, "event-not-found");
        assertRefused(get("bob", "/events/" + UNKNOWN + "/check-ins/me"), 404, "event-not-found");
    }

    @Test
    void aMemberWhoIsNotCheckedInHasNoCheckIn() {
        assertRefused(mine("bob"), 404, "check-in-not-found");
    }

    @Test
    void checkingInRequiresSigningIn() {
        client().put()
                .uri("/events/" + eventId + "/check-ins/" + bobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void attendanceListsEveryConfirmedPartyWithTotals() {
        put("carol", "/events/" + eventId + "/rsvps/me", Map.of("guests", 0))
                .expectStatus()
                .isOk();
        clock.set(STARTS);
        checkIn("alice", bobId, 2).expectStatus().isOk();

        attendance("alice")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.parties.length()")
                .isEqualTo(1)
                .jsonPath("$.parties[0].memberId")
                .isEqualTo(bobId)
                .jsonPath("$.parties[0].partySize")
                .isEqualTo(3)
                .jsonPath("$.parties[0].arrived")
                .isEqualTo(2)
                .jsonPath("$.seatsConfirmed")
                .isEqualTo(3)
                .jsonPath("$.peopleCheckedIn")
                .isEqualTo(2);
    }

    @Test
    void attendanceShowsPartiesNotCheckedInYetAndStaysAvailableAfterCheckInCloses() {
        attendance("alice")
                .expectBody()
                .jsonPath("$.parties[0].memberId")
                .isEqualTo(bobId)
                .jsonPath("$.parties[0].arrived")
                .isEmpty()
                .jsonPath("$.seatsConfirmed")
                .isEqualTo(3)
                .jsonPath("$.peopleCheckedIn")
                .isEqualTo(0);
        clock.set(ENDS.plus(Duration.ofDays(1)));
        attendance("alice").expectStatus().isOk();
    }

    @Test
    void attendanceIsRefusedToNonOrganizersAndSignedOutVisitors() {
        assertRefused(attendance("bob"), 403, "not-an-organizer");
        assertRefused(attendance("carol"), 403, "not-an-organizer");
        assertRefused(attendance(null), 401, "not-authenticated");
    }

    @Test
    void attendanceOfAnUnknownEventIsNotFound() {
        assertRefused(get("alice", "/events/" + UNKNOWN + "/attendance"), 404, "event-not-found");
        assertRefused(get("alice", "/events/nope/attendance"), 404, "event-not-found");
    }

    @Test
    void undoingACheckInRemovesIt() {
        clock.set(STARTS);
        checkIn("alice", bobId, 2).expectStatus().isOk();
        undo("alice", bobId).expectStatus().isNoContent();
        assertRefused(mine("bob"), 404, "check-in-not-found");
        checkIn("alice", bobId, null).expectBody().jsonPath("$.arrived").isEqualTo(3);
    }

    @Test
    void undoingWhenNotCheckedInIsNotFound() {
        clock.set(STARTS);
        assertRefused(undo("alice", bobId), 404, "check-in-not-found");
        checkIn("alice", bobId, null).expectStatus().isOk();
        undo("alice", bobId).expectStatus().isNoContent();
        assertRefused(undo("alice", bobId), 404, "check-in-not-found");
    }

    @Test
    void undoIsRefusedToANonOrganizer() {
        clock.set(STARTS);
        checkIn("alice", bobId, null).expectStatus().isOk();
        assertRefused(undo("carol", bobId), 403, "not-an-organizer");
        assertRefused(undo("bob", bobId), 403, "not-an-organizer");
        mine("bob").expectStatus().isOk();
    }

    @Test
    void undoIsRefusedOutsideTheWindow() {
        clock.set(STARTS);
        checkIn("alice", bobId, null).expectStatus().isOk();
        clock.set(ENDS);
        assertRefused(undo("alice", bobId), 422, "check-in-closed");
        clock.set(STARTS.minus(Duration.ofHours(1)).minusSeconds(1));
        assertRefused(undo("alice", bobId), 422, "check-in-closed");
        clock.set(ENDS.minusSeconds(1));
        mine("bob").expectStatus().isOk();
    }

    @Test
    void undoIsRefusedForACancelledEvent() {
        clock.set(STARTS);
        checkIn("alice", bobId, null).expectStatus().isOk();
        post("alice", "/events/" + eventId + "/cancellation", Map.of())
                .expectStatus()
                .isOk();
        assertRefused(undo("alice", bobId), 422, "check-in-closed");
    }

    @Test
    void undoWithUnknownOrMalformedIdsIsNotFound() {
        clock.set(STARTS);
        assertRefused(undo("alice", bobId, UNKNOWN), 404, "event-not-found");
        assertRefused(undo("alice", bobId, "nope"), 404, "event-not-found");
        assertRefused(delete("alice", "/events/" + eventId + "/check-ins/nope"), 404, "member-not-found");
    }

    @Test
    void undoRequiresSigningIn() {
        client().delete()
                .uri("/events/" + eventId + "/check-ins/" + bobId)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    private ResponseSpec attendance(String member) {
        return get(member, "/events/" + eventId + "/attendance");
    }

    private ResponseSpec undo(String member, String memberId) {
        return undo(member, memberId, eventId);
    }

    private ResponseSpec undo(String member, String memberId, String event) {
        return delete(member, "/events/" + event + "/check-ins/" + memberId);
    }

    private ResponseSpec delete(String member, String path) {
        return client().delete()
                .uri(path)
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .exchange();
    }

    private Map<String, Object> event(int guestLimit) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("title", "Monthly meetup");
        json.put("description", "Talks");
        json.put("venue", "123 Main St");
        json.put("startsAt", STARTS.toString());
        json.put("endsAt", ENDS.toString());
        json.put("capacity", 3);
        json.put("guestLimit", guestLimit);
        return json;
    }

    private ResponseSpec checkIn(String member, String memberId, Integer arrived) {
        Object body = arrived == null ? Map.of() : Map.of("arrived", arrived);
        return put(member, "/events/" + eventId + "/check-ins/" + memberId, body);
    }

    private ResponseSpec mine(String member) {
        return get(member, "/events/" + eventId + "/check-ins/me");
    }

    private String organizerId(String member) {
        String group = id(post(member, "/groups", Map.of("name", "Id of " + member)));
        String[] holder = new String[1];
        get(null, "/groups/" + group)
                .expectBody()
                .jsonPath("$.organizers[0].id")
                .value(String.class, value -> holder[0] = value);
        return Objects.requireNonNull(holder[0]);
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private ResponseSpec get(String member, String path) {
        return client().get()
                .uri(path)
                .headers(h -> {
                    if (member != null) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .exchange();
    }

    private ResponseSpec post(String member, String path, Object body) {
        return client().post()
                .uri(path)
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private ResponseSpec put(String member, String path, Object body) {
        return client().put()
                .uri(path)
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private String id(ResponseSpec response) {
        String[] holder = new String[1];
        response.expectBody().jsonPath("$.id").value((Object value) -> holder[0] = String.valueOf(value));
        return Objects.requireNonNull(holder[0]);
    }

    private static void assertRefused(ResponseSpec response, int status, String rule) {
        response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://rsvp-factory.dev/problems/" + rule);
    }
}
