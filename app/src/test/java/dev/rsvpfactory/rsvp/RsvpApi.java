package dev.rsvpfactory.rsvp;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Drives the RSVP API on a running server as the seeded demo members. */
final class RsvpApi {

    static final String PASSWORD = "rsvp-demo";
    static final String STARTS = "2030-04-01T18:00:00Z";

    private final int port;

    RsvpApi(int port) {
        this.port = port;
    }

    RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    String id(ResponseSpec response) {
        String[] id = new String[1];
        response.expectBody().jsonPath("$.id").value((Object value) -> id[0] = String.valueOf(value));
        return Objects.requireNonNull(id[0]);
    }

    String groupWith(String organizer, String... members) {
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

    String event(String groupId, int capacity, int guestLimit, String cutoff) {
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
                        guestLimit,
                        "rsvpCutoff",
                        cutoff))
                .exchange()
                .expectStatus()
                .isCreated());
    }

    String event(String groupId, int capacity, int guestLimit) {
        return event(groupId, capacity, guestLimit, STARTS);
    }

    ResponseSpec rsvp(String member, String eventId, Object guests) {
        return client().put()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("guests", guests))
                .exchange();
    }

    ResponseSpec mine(String member, String eventId) {
        return client().get()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .exchange();
    }

    ResponseSpec withdraw(String member, String eventId) {
        return client().delete()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .exchange();
    }

    void assertOpenSeats(String eventId, int openSeats, int waitlistLength) {
        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.openSeats")
                .isEqualTo(openSeats)
                .jsonPath("$.waitlistLength")
                .isEqualTo(waitlistLength);
    }

    static void assertRefused(ResponseSpec response, int status, String rule) {
        response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://rsvp-factory.dev/problems/" + rule);
    }
}
