package dev.rsvpfactory.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * The HTTP API as scenarios see it. Requests are made as a seeded demo member, or as nobody when {@code member} is
 * null. The contract (paths, JSON fields, problem types) is documented in {@code acceptance/README.md}.
 */
final class Api {

    static final String PASSWORD = "rsvp-demo";

    private final RestTestClient client;

    Api(int port) {
        this.client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    // Raw requests, for asserting on responses.

    ResponseSpec get(@Nullable String member, String path) {
        return client.get().uri(path).headers(h -> signIn(h, member)).exchange();
    }

    ResponseSpec post(@Nullable String member, String path, Object body) {
        return client.post()
                .uri(path)
                .headers(h -> signIn(h, member))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    ResponseSpec postWithPassword(String member, String password, String path, Object body) {
        return client.post()
                .uri(path)
                .headers(h -> h.setBasicAuth(member, password))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    ResponseSpec put(@Nullable String member, String path, Object body) {
        return client.put()
                .uri(path)
                .headers(h -> signIn(h, member))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    ResponseSpec delete(@Nullable String member, String path) {
        return client.delete().uri(path).headers(h -> signIn(h, member)).exchange();
    }

    // Steps that set up a scenario. Each one asserts it succeeded, so a broken step fails where it happens.

    String startGroup(String member) {
        return id(post(member, "/groups", Map.of("name", "Group of " + member, "description", "A test group"))
                .expectStatus()
                .isCreated());
    }

    void join(String member, String groupId) {
        put(member, "/groups/" + groupId + "/members/me", Map.of())
                .expectStatus()
                .is2xxSuccessful();
    }

    void leave(String member, String groupId) {
        delete(member, "/groups/" + groupId + "/members/me").expectStatus().is2xxSuccessful();
    }

    String scheduleEvent(String organizer, String groupId, EventRequest event) {
        return id(post(organizer, "/groups/" + groupId + "/events", event.toJson())
                .expectStatus()
                .isCreated());
    }

    ResponseSpec rsvp(String member, String eventId, int guests) {
        return put(member, "/events/" + eventId + "/rsvps/me", Map.of("guests", guests));
    }

    ResponseSpec myRsvp(String member, String eventId) {
        return get(member, "/events/" + eventId + "/rsvps/me");
    }

    ResponseSpec withdraw(String member, String eventId) {
        return delete(member, "/events/" + eventId + "/rsvps/me");
    }

    ResponseSpec event(String eventId) {
        return get(null, "/events/" + eventId);
    }

    /** The id of a seeded demo member, as the API shows it on the group they started. */
    String memberId(String member) {
        String groupId = startGroup(member);
        String[] holder = new String[1];
        get(null, "/groups/" + groupId)
                .expectBody()
                .jsonPath("$.organizers[0].id")
                .value(String.class, id -> holder[0] = id);
        return Objects.requireNonNull(holder[0], "group had no organizer id");
    }

    /** A group started by {@code organizer} that every other listed member has joined. */
    String groupWith(String organizer, String... members) {
        String groupId = startGroup(organizer);
        for (String member : members) {
            join(member, groupId);
        }
        return groupId;
    }

    // Assertions shared by many scenarios.

    static void assertRefused(ResponseSpec response, HttpStatus status, String rule) {
        response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .value(String.class, type -> assertThat(type).endsWith("/" + rule));
    }

    static void assertConfirmed(ResponseSpec response, int partySize) {
        response.expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(partySize);
    }

    static void assertWaitlisted(ResponseSpec response, int position) {
        response.expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("waitlisted")
                .jsonPath("$.waitlistPosition")
                .isEqualTo(position);
    }

    void assertOpenSeats(String eventId, int openSeats) {
        event(eventId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.openSeats")
                .isEqualTo(openSeats);
    }

    private static String id(ResponseSpec response) {
        String[] holder = new String[1];
        response.expectBody().jsonPath("$.id").value(Object.class, id -> holder[0] = String.valueOf(id));
        return Objects.requireNonNull(holder[0], "response had no id");
    }

    private static void signIn(HttpHeaders headers, @Nullable String member) {
        if (member != null) {
            headers.setBasicAuth(member, PASSWORD);
        }
    }

    /** An event to schedule. Defaults: capacity 10, no guest limit, no cutoff (so the app's defaults apply). */
    record EventRequest(
            Instant startsAt,
            Instant endsAt,
            int capacity,
            @Nullable Integer guestLimit,
            @Nullable Instant rsvpCutoff) {

        static EventRequest startingAt(Instant startsAt) {
            return new EventRequest(startsAt, startsAt.plusSeconds(2 * 3600), 10, null, null);
        }

        EventRequest withCapacity(int capacity) {
            return new EventRequest(startsAt, endsAt, capacity, guestLimit, rsvpCutoff);
        }

        EventRequest withGuestLimit(int guestLimit) {
            return new EventRequest(startsAt, endsAt, capacity, guestLimit, rsvpCutoff);
        }

        EventRequest withRsvpCutoff(Instant rsvpCutoff) {
            return new EventRequest(startsAt, endsAt, capacity, guestLimit, rsvpCutoff);
        }

        EventRequest endingAt(Instant endsAt) {
            return new EventRequest(startsAt, endsAt, capacity, guestLimit, rsvpCutoff);
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("title", "Monthly meetup");
            json.put("description", "Talks and pizza");
            json.put("venue", "123 Main St");
            json.put("startsAt", startsAt.toString());
            json.put("endsAt", endsAt.toString());
            json.put("capacity", capacity);
            if (guestLimit != null) {
                json.put("guestLimit", guestLimit);
            }
            if (rsvpCutoff != null) {
                json.put("rsvpCutoff", rsvpCutoff.toString());
            }
            return json;
        }
    }
}
