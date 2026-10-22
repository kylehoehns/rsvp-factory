package dev.rsvpfactory.events;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for scheduling and viewing events: the HTTP API against a real Postgres, as the seeded demo members. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulingEventsApiTests {

    private static final String PASSWORD = "rsvp-demo";
    private static final String STARTS = "2030-04-01T18:00:00Z";
    private static final String ENDS = "2030-04-01T20:00:00Z";

    @Value("${local.server.port}")
    int port;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String startGroup(String member) {
        String[] id = new String[1];
        client().post()
                .uri("/groups")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Chess club"))
                .exchange()
                .expectBody()
                .jsonPath("$.id")
                .value(Object.class, value -> id[0] = String.valueOf(value));
        return Objects.requireNonNull(id[0]);
    }

    private static Map<String, Object> event(Object... overrides) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("title", "Monthly meetup");
        json.put("description", "Talks and pizza");
        json.put("venue", "123 Main St");
        json.put("startsAt", STARTS);
        json.put("endsAt", ENDS);
        json.put("capacity", 10);
        for (int i = 0; i < overrides.length; i += 2) {
            json.put((String) overrides[i], overrides[i + 1]);
        }
        return json;
    }

    private ResponseSpec schedule(String member, String groupId, Map<String, Object> body) {
        return client().post()
                .uri("/groups/" + groupId + "/events")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private String scheduled(String organizer, String groupId, Map<String, Object> body) {
        String[] id = new String[1];
        schedule(organizer, groupId, body)
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .value(Object.class, value -> id[0] = String.valueOf(value));
        return Objects.requireNonNull(id[0]);
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

    @Test
    void anOrganizerSchedulesAnEventAndAnyoneSeesItWithItsOpenSeats() {
        String groupId = startGroup("alice");

        String eventId = scheduled("alice", groupId, event("guestLimit", 2, "rsvpCutoff", "2030-04-01T12:00:00Z"));

        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(eventId)
                .jsonPath("$.groupId")
                .isEqualTo(groupId)
                .jsonPath("$.title")
                .isEqualTo("Monthly meetup")
                .jsonPath("$.description")
                .isEqualTo("Talks and pizza")
                .jsonPath("$.venue")
                .isEqualTo("123 Main St")
                .jsonPath("$.startsAt")
                .isEqualTo(STARTS)
                .jsonPath("$.endsAt")
                .isEqualTo(ENDS)
                .jsonPath("$.status")
                .isEqualTo("scheduled")
                .jsonPath("$.capacity")
                .isEqualTo(10)
                .jsonPath("$.guestLimit")
                .isEqualTo(2)
                .jsonPath("$.rsvpCutoff")
                .isEqualTo("2030-04-01T12:00:00Z")
                .jsonPath("$.openSeats")
                .isEqualTo(10)
                .jsonPath("$.waitlistLength")
                .isEqualTo(0);
    }

    @Test
    void theGuestLimitDefaultsToZeroAndTheRsvpCutoffToTheStart() {
        String groupId = startGroup("alice");

        String eventId = scheduled("alice", groupId, event());

        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.guestLimit")
                .isEqualTo(0)
                .jsonPath("$.rsvpCutoff")
                .isEqualTo(STARTS);
    }

    @Test
    void aDescriptionIsOptional() {
        String groupId = startGroup("alice");
        Map<String, Object> body = event();
        body.remove("description");

        String eventId = scheduled("alice", groupId, body);

        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.description")
                .isEqualTo("");
    }

    @Test
    void timestampsMayCarryAnOffsetAndAreReturnedAsInstants() {
        String groupId = startGroup("alice");

        String eventId = scheduled("alice", groupId, event("startsAt", "2030-04-01T20:00:00+02:00"));

        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.startsAt")
                .isEqualTo(STARTS);
    }

    @Test
    void aGroupListsItsEventsEarliestFirstWithoutSigningIn() {
        String groupId = startGroup("alice");
        String later = scheduled(
                "alice",
                groupId,
                event("title", "Later", "startsAt", "2030-05-01T18:00:00Z", "endsAt", "2030-05-01T19:00:00Z"));
        String sooner = scheduled("alice", groupId, event("title", "Sooner", "capacity", 3));
        scheduled("bob", startGroup("bob"), event("title", "Elsewhere"));

        client().get()
                .uri("/groups/" + groupId + "/events")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].id")
                .isEqualTo(sooner)
                .jsonPath("$[0].title")
                .isEqualTo("Sooner")
                .jsonPath("$[0].capacity")
                .isEqualTo(3)
                .jsonPath("$[0].openSeats")
                .isEqualTo(3)
                .jsonPath("$[0].waitlistLength")
                .isEqualTo(0)
                .jsonPath("$[0].status")
                .isEqualTo("scheduled")
                .jsonPath("$[1].id")
                .isEqualTo(later);
    }

    @Test
    void aGroupWithoutEventsListsNone() {
        client().get()
                .uri("/groups/" + startGroup("alice") + "/events")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
    }

    @Test
    void aMemberWhoIsNotAnOrganizerIsRefused() {
        String groupId = startGroup("alice");

        assertRefused(schedule("bob", groupId, event()), 403, "not-an-organizer");
    }

    @Test
    void aVisitorIsRefusedAsNotAuthenticated() {
        assertRefused(schedule("", startGroup("alice"), event()), 401, "not-authenticated");
    }

    @Test
    void schedulingForAnUnknownGroupIsNotFound() {
        assertRefused(schedule("alice", "999999", event()), 404, "group-not-found");
        assertRefused(schedule("alice", "not-an-id", event()), 404, "group-not-found");
        assertRefused(client().get().uri("/groups/999999/events").exchange(), 404, "group-not-found");
        assertRefused(client().get().uri("/groups/not-an-id/events").exchange(), 404, "group-not-found");
    }

    @Test
    void capacityMustBePositive() {
        String groupId = startGroup("alice");

        assertRefused(schedule("alice", groupId, event("capacity", 0)), 400, "capacity-not-positive");
        assertRefused(schedule("alice", groupId, event("capacity", -5)), 400, "capacity-not-positive");
        scheduled("alice", groupId, event("capacity", 1));
    }

    @Test
    void theEndMustBeAfterTheStart() {
        String groupId = startGroup("alice");

        assertRefused(schedule("alice", groupId, event("endsAt", STARTS)), 400, "end-not-after-start");
        assertRefused(schedule("alice", groupId, event("endsAt", "2030-04-01T17:00:00Z")), 400, "end-not-after-start");
        scheduled("alice", groupId, event("endsAt", "2030-04-01T18:00:01Z"));
    }

    @Test
    void theRsvpCutoffMustBeNoLaterThanTheStart() {
        String groupId = startGroup("alice");

        assertRefused(
                schedule("alice", groupId, event("rsvpCutoff", "2030-04-01T18:00:01Z")),
                400,
                "rsvp-cutoff-after-start");
        scheduled("alice", groupId, event("rsvpCutoff", STARTS));
    }

    @Test
    void theGuestLimitCannotBeNegative() {
        String groupId = startGroup("alice");

        assertRefused(schedule("alice", groupId, event("guestLimit", -1)), 400, "guest-limit-negative");
        scheduled("alice", groupId, event("guestLimit", 0));
    }

    @Test
    void missingFieldsAreValidationProblems() {
        String groupId = startGroup("alice");
        for (String field : new String[] {"title", "venue", "startsAt", "endsAt", "capacity"}) {
            Map<String, Object> body = event();
            body.remove(field);
            assertRefused(schedule("alice", groupId, body), 400, "validation-failed");
        }
        assertRefused(schedule("alice", groupId, event("title", " ")), 400, "validation-failed");
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertRefused(client().get().uri("/events/999999").exchange(), 404, "event-not-found");
        assertRefused(client().get().uri("/events/not-an-id").exchange(), 404, "event-not-found");
    }
}
