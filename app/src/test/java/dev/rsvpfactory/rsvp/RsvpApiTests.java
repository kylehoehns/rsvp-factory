package dev.rsvpfactory.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for RSVPing: the HTTP API against a real Postgres, as the seeded demo members. */
@Import({TestcontainersConfiguration.class, RsvpApiTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RsvpApiTests {

    private static final String PASSWORD = "rsvp-demo";
    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final String STARTS = "2030-04-01T18:00:00Z";

    @TestConfiguration(proxyBeanMethods = false)
    static class Setup {

        @org.springframework.context.annotation.Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, java.time.ZoneOffset.UTC);
        }

        @Component
        static class Published {
            final List<Object> events = new CopyOnWriteArrayList<>();

            @EventListener
            void on(RsvpConfirmed event) {
                events.add(event);
            }

            @EventListener
            void on(RsvpWaitlisted event) {
                events.add(event);
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

    private String event(String groupId, int capacity, int guestLimit, String cutoff) {
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

    private String event(String groupId, int capacity, int guestLimit) {
        return event(groupId, capacity, guestLimit, STARTS);
    }

    private ResponseSpec rsvp(String member, String eventId, Object guests) {
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

    private ResponseSpec mine(String member, String eventId) {
        return client().get()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> {
                    if (!member.isEmpty()) {
                        h.setBasicAuth(member, PASSWORD);
                    }
                })
                .exchange();
    }

    private void assertOpenSeats(String eventId, int openSeats, int waitlistLength) {
        client().get()
                .uri("/events/" + eventId)
                .exchange()
                .expectBody()
                .jsonPath("$.openSeats")
                .isEqualTo(openSeats)
                .jsonPath("$.waitlistLength")
                .isEqualTo(waitlistLength);
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
    void aPartyThatFitsIsConfirmedAndTakesItsSeats() {
        String eventId = event(groupWith("alice", "bob"), 5, 2);

        rsvp("bob", eventId, 2)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(3)
                .jsonPath("$.waitlistPosition")
                .doesNotExist();

        mine("bob", eventId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(3);
        assertOpenSeats(eventId, 2, 0);
    }

    @Test
    void aPartyThatDoesNotFitIsWaitlistedWithAPositionAndTakesNoSeats() {
        String eventId = event(groupWith("alice", "bob", "carol", "dave"), 3, 2);
        rsvp("bob", eventId, 2).expectStatus().isOk();

        rsvp("carol", eventId, 0)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("waitlisted")
                .jsonPath("$.partySize")
                .isEqualTo(1)
                .jsonPath("$.waitlistPosition")
                .isEqualTo(1);
        rsvp("dave", eventId, 1).expectStatus().isOk();

        mine("dave", eventId)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("waitlisted")
                .jsonPath("$.waitlistPosition")
                .isEqualTo(2);
        assertOpenSeats(eventId, 0, 2);
    }

    @Test
    void rsvpingAgainWithTheSamePartyKeepsOneRsvpAndTheSameSeats() {
        String eventId = event(groupWith("alice", "bob", "carol"), 3, 2);
        rsvp("bob", eventId, 1).expectStatus().isOk();
        rsvp("carol", eventId, 1).expectStatus().isOk();

        rsvp("bob", eventId, 1)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(2);
        rsvp("carol", eventId, 1)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("waitlisted")
                .jsonPath("$.waitlistPosition")
                .isEqualTo(1);

        assertOpenSeats(eventId, 1, 1);
    }

    @Test
    void aMemberWhoIsNotInTheGroupIsRefused() {
        String eventId = event(groupWith("alice"), 5, 0);

        assertRefused(rsvp("bob", eventId, 0), 403, "not-a-member");
        mine("bob", eventId).expectStatus().isNotFound();
        assertOpenSeats(eventId, 5, 0);
    }

    @Test
    void moreGuestsThanTheGuestLimitIsRefused() {
        String eventId = event(groupWith("alice", "bob"), 10, 1);

        assertRefused(rsvp("bob", eventId, 2), 422, "guest-limit-exceeded");
        assertOpenSeats(eventId, 10, 0);
    }

    @Test
    void aNegativeGuestCountIsInvalid() {
        String eventId = event(groupWith("alice", "bob"), 10, 1);

        assertRefused(rsvp("bob", eventId, -1), 400, "validation-failed");
    }

    @Test
    void aMissingGuestCountMeansNoGuests() {
        String eventId = event(groupWith("alice", "bob"), 10, 1);

        client().put()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> h.setBasicAuth("bob", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectBody()
                .jsonPath("$.partySize")
                .isEqualTo(1);
    }

    @Test
    void anRsvpAtTheCutoffIsRefused() {
        String eventId = event(groupWith("alice", "bob"), 10, 0, NOW.toString());

        assertRefused(rsvp("bob", eventId, 0), 409, "rsvp-cutoff-passed");
    }

    @Test
    void changingTheGuestsOfAnExistingRsvpIsRefusedForNow() {
        String eventId = event(groupWith("alice", "bob"), 10, 2);
        rsvp("bob", eventId, 0).expectStatus().isOk();

        assertRefused(rsvp("bob", eventId, 1), 409, "rsvp-party-change-unsupported");
        assertOpenSeats(eventId, 9, 0);
    }

    @Test
    void anUnknownOrMalformedEventIsNotFound() {
        assertRefused(rsvp("bob", "999999", 0), 404, "event-not-found");
        assertRefused(rsvp("bob", "nope", 0), 404, "event-not-found");
        assertRefused(mine("bob", "999999"), 404, "event-not-found");
    }

    @Test
    void someoneWhoIsNotSignedInIsNotAuthenticated() {
        String eventId = event(groupWith("alice"), 5, 0);

        assertRefused(rsvp("", eventId, 0), 401, "not-authenticated");
        assertRefused(mine("", eventId), 401, "not-authenticated");
    }

    @Test
    void eachMemberSeesTheirOwnRsvp() {
        String eventId = event(groupWith("alice", "bob", "carol"), 10, 2);
        rsvp("bob", eventId, 2).expectStatus().isOk();
        rsvp("carol", eventId, 0).expectStatus().isOk();

        mine("carol", eventId).expectBody().jsonPath("$.partySize").isEqualTo(1);
        mine("bob", eventId).expectBody().jsonPath("$.partySize").isEqualTo(3);
    }

    @Test
    void aMemberWithoutAnRsvpHasNothingToShow() {
        String eventId = event(groupWith("alice", "bob"), 5, 0);

        assertRefused(mine("bob", eventId), 404, "rsvp-not-found");
    }

    @Test
    void confirmedAndWaitlistedRsvpsArePublishedAsEvents() {
        String eventId = event(groupWith("alice", "bob", "carol"), 2, 1);
        long event = Long.parseLong(eventId);

        rsvp("bob", eventId, 1).expectStatus().isOk();
        rsvp("carol", eventId, 0).expectStatus().isOk();
        rsvp("bob", eventId, 1).expectStatus().isOk();

        List<Object> forEvent = published.events.stream()
                .filter(e -> (e instanceof RsvpConfirmed c && c.eventId() == event)
                        || (e instanceof RsvpWaitlisted w && w.eventId() == event))
                .toList();
        assertThat(forEvent).hasSize(2);
        assertThat(forEvent.get(0))
                .isInstanceOfSatisfying(
                        RsvpConfirmed.class, c -> assertThat(c.partySize()).isEqualTo(2));
        assertThat(forEvent.get(1)).isInstanceOfSatisfying(RsvpWaitlisted.class, w -> {
            assertThat(w.partySize()).isEqualTo(1);
            assertThat(w.position()).isEqualTo(1);
        });
    }
}
