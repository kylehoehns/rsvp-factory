package dev.rsvpfactory.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for changing an event's capacity: the HTTP API against a real Postgres, as the seeded demo members. */
@Import({TestcontainersConfiguration.class, CapacityApiTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapacityApiTests {

    private static final String PASSWORD = "rsvp-demo";
    private static final String STARTS = "2030-04-01T18:00:00Z";

    @TestConfiguration(proxyBeanMethods = false)
    static class Setup {

        @Component
        static class Promotions {
            final List<RsvpPromoted> promoted = new CopyOnWriteArrayList<>();

            @EventListener
            void on(RsvpPromoted event) {
                promoted.add(event);
            }
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    Setup.Promotions promotions;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String id(ResponseSpec response) {
        String[] id = new String[1];
        response.expectBody().jsonPath("$.id").value((Object value) -> id[0] = String.valueOf(value));
        return Objects.requireNonNull(id[0]);
    }

    private String groupWith(String organizer, String... members) {
        String groupId = id(client().post()
                .uri("/groups")
                .headers(h -> h.setBasicAuth(organizer, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Chess club"))
                .exchange());
        for (String member : members) {
            client().put()
                    .uri("/groups/" + groupId + "/members/me")
                    .headers(h -> h.setBasicAuth(member, PASSWORD))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .exchange()
                    .expectStatus()
                    .is2xxSuccessful();
        }
        return groupId;
    }

    private String event(String groupId, int capacity) {
        return id(client().post()
                .uri("/groups/" + groupId + "/events")
                .headers(h -> h.setBasicAuth("alice", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title",
                        "Monthly meetup",
                        "description",
                        "Talks",
                        "venue",
                        "123 Main St",
                        "startsAt",
                        STARTS,
                        "endsAt",
                        "2030-04-01T20:00:00Z",
                        "capacity",
                        capacity,
                        "guestLimit",
                        2,
                        "rsvpCutoff",
                        STARTS))
                .exchange()
                .expectStatus()
                .isCreated());
    }

    private ResponseSpec rsvp(String member, String eventId, int guests) {
        return client().put()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("guests", guests))
                .exchange();
    }

    private ResponseSpec changeCapacity(String member, String eventId, Object capacity) {
        return client().put()
                .uri("/events/" + eventId + "/capacity")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("capacity", capacity))
                .exchange();
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

    private void assertStatus(String member, String eventId, String status) {
        client().get()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(status);
    }

    @Test
    void raisingTheCapacityPromotesFromTheWaitlistAndReturnsTheEvent() {
        String eventId = event(groupWith("alice", "bob", "carol", "dave"), 2);
        rsvp("bob", eventId, 1).expectStatus().isOk();
        rsvp("carol", eventId, 1).expectStatus().isOk();
        rsvp("dave", eventId, 0).expectStatus().isOk();
        int before = promotions.promoted.size();

        changeCapacity("alice", eventId, 5)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.capacity")
                .isEqualTo(5)
                .jsonPath("$.openSeats")
                .isEqualTo(0)
                .jsonPath("$.waitlistLength")
                .isEqualTo(0);

        assertStatus("carol", eventId, "confirmed");
        assertStatus("dave", eventId, "confirmed");
        assertThat(promotions.promoted.subList(before, promotions.promoted.size()))
                .extracting(RsvpPromoted::partySize)
                .containsExactly(2, 1);
    }

    @Test
    void loweringTheCapacityToTheConfirmedSeatsIsAllowedAndBelowThemIsRefused() {
        String eventId = event(groupWith("alice", "bob"), 5);
        rsvp("bob", eventId, 2).expectStatus().isOk();

        assertRefused(changeCapacity("alice", eventId, 2), 409, "capacity-below-confirmed-seats");
        changeCapacity("alice", eventId, 3)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.capacity")
                .isEqualTo(3)
                .jsonPath("$.openSeats")
                .isEqualTo(0);
        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.capacity")
                .isEqualTo(3);
    }

    @Test
    void aCapacityThatIsNotPositiveIsRefused() {
        String eventId = event(groupWith("alice"), 5);

        assertRefused(changeCapacity("alice", eventId, 0), 400, "capacity-not-positive");
    }

    @Test
    void aMemberWhoIsNotAnOrganizerIsRefused() {
        String eventId = event(groupWith("alice", "bob"), 5);

        assertRefused(changeCapacity("bob", eventId, 9), 403, "not-an-organizer");
        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.capacity")
                .isEqualTo(5);
    }

    @Test
    void anUnknownEventIsNotFoundAndAnAnonymousCallerIsNotAuthenticated() {
        assertRefused(changeCapacity("alice", "999999", 5), 404, "event-not-found");
        changeCapacity("", "1", 5).expectStatus().isUnauthorized();
    }
}
