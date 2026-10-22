package dev.rsvpfactory.events;

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

/** Seam A for cancelling an event: the HTTP API against a real Postgres, as the seeded demo members. */
@Import({TestcontainersConfiguration.class, CancellingEventsApiTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CancellingEventsApiTests {

    private static final String PASSWORD = "rsvp-demo";

    @TestConfiguration(proxyBeanMethods = false)
    static class Setup {

        @Component
        static class Published {
            final List<EventCancelled> cancelled = new CopyOnWriteArrayList<>();

            @EventListener
            void on(EventCancelled event) {
                cancelled.add(event);
            }
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    Setup.Published published;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String id(ResponseSpec response) {
        String[] id = new String[1];
        response.expectBody().jsonPath("$.id").value(Object.class, value -> id[0] = String.valueOf(value));
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
                        "title", "Monthly meetup",
                        "description", "Talks",
                        "venue", "123 Main St",
                        "startsAt", "2999-04-01T18:00:00Z",
                        "endsAt", "2999-04-01T20:00:00Z",
                        "capacity", capacity))
                .exchange()
                .expectStatus()
                .isCreated());
    }

    private ResponseSpec rsvp(String member, String eventId) {
        return client().put()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("guests", 0))
                .exchange();
    }

    private ResponseSpec cancel(String member, String eventId) {
        return client().post()
                .uri("/events/" + eventId + "/cancellation")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange();
    }

    private ResponseSpec view(String eventId) {
        return client().get().uri("/events/" + eventId).exchange();
    }

    private static void assertProblem(ResponseSpec response, int status, String rule) {
        response.expectStatus()
                .isEqualTo(status)
                .expectBody()
                .jsonPath("$.type")
                .value(String.class, type -> assertThat(type).endsWith(rule));
    }

    @Test
    void anOrganizerCancelsAnEventAndItStaysVisibleAsCancelled() {
        String groupId = groupWith("alice");
        String eventId = event(groupId, 5);

        cancel("alice", eventId)
                .expectStatus()
                .is2xxSuccessful()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("cancelled");

        view(eventId).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("cancelled");
    }

    @Test
    void aMemberWhoIsNotAnOrganizerIsRefusedAndTheEventStaysScheduled() {
        String groupId = groupWith("alice", "bob");
        String eventId = event(groupId, 5);

        assertProblem(cancel("bob", eventId), 403, "not-an-organizer");

        view(eventId).expectBody().jsonPath("$.status").isEqualTo("scheduled");
    }

    @Test
    void aVisitorWhoIsNotSignedInIsNotAuthenticated() {
        String eventId = event(groupWith("alice"), 5);

        cancel("", eventId).expectStatus().isUnauthorized();
    }

    @Test
    void cancellingAnEventThatDoesNotExistIsNotFound() {
        assertProblem(cancel("alice", "999999"), 404, "event-not-found");
        assertProblem(cancel("alice", "abc"), 404, "event-not-found");
    }

    @Test
    void cancellingTwiceIsHarmlessAndPublishesOnce() {
        String eventId = event(groupWith("alice"), 5);

        cancel("alice", eventId).expectStatus().is2xxSuccessful();
        cancel("alice", eventId)
                .expectStatus()
                .is2xxSuccessful()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("cancelled");

        assertThat(published.cancelled)
                .filteredOn(e -> e.eventId() == Long.parseLong(eventId))
                .hasSize(1);
    }

    @Test
    void cancellingPublishesAnEventWithTheEventAndGroup() {
        String groupId = groupWith("alice");
        String eventId = event(groupId, 5);

        cancel("alice", eventId).expectStatus().is2xxSuccessful();

        assertThat(published.cancelled).contains(new EventCancelled(Long.parseLong(eventId), Long.parseLong(groupId)));
    }

    @Test
    void everyRsvpEndsWithoutPromotionsAndNewRsvpsAreRefused() {
        String groupId = groupWith("alice", "bob", "carol");
        String eventId = event(groupId, 1);
        rsvp("bob", eventId)
                .expectStatus()
                .is2xxSuccessful()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed");
        rsvp("carol", eventId).expectBody().jsonPath("$.status").isEqualTo("waitlisted");

        cancel("alice", eventId).expectStatus().is2xxSuccessful();

        view(eventId)
                .expectBody()
                .jsonPath("$.openSeats")
                .isEqualTo(1)
                .jsonPath("$.waitlistLength")
                .isEqualTo(0);
        for (String member : List.of("bob", "carol")) {
            client().get()
                    .uri("/events/" + eventId + "/rsvps/me")
                    .headers(h -> h.setBasicAuth(member, PASSWORD))
                    .exchange()
                    .expectStatus()
                    .isNotFound();
        }
        assertProblem(rsvp("bob", eventId), 409, "event-cancelled");
    }
}
