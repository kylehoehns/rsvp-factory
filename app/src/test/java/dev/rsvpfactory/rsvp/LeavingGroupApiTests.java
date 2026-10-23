package dev.rsvpfactory.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for leaving a group: the HTTP API against a real Postgres, with a clock the test moves. */
@Import({TestcontainersConfiguration.class, LeavingGroupApiTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LeavingGroupApiTests {

    private static final String PASSWORD = "rsvp-demo";
    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");
    private static final String STARTS = "2030-04-01T18:00:00Z";
    private static final Instant AFTER_START = Instant.parse("2030-04-02T00:00:00Z");

    static final AtomicReference<Instant> NOW_REF = new AtomicReference<>(NOW);

    @TestConfiguration(proxyBeanMethods = false)
    static class Setup {

        @Bean
        @Primary
        Clock movableClock() {
            return new Clock() {
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
                    return NOW_REF.get();
                }
            };
        }

        @Component
        static class Published {
            final List<Object> events = new CopyOnWriteArrayList<>();

            @EventListener
            void on(RsvpWithdrawn event) {
                events.add(event);
            }

            @EventListener
            void on(RsvpPromoted event) {
                events.add(event);
            }
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    Setup.Published published;

    @BeforeEach
    void resetClock() {
        NOW_REF.set(NOW);
    }

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

    private String event(String groupId, String startsAt, String endsAt, int capacity) {
        return id(client().post()
                .uri("/groups/" + groupId + "/events")
                .headers(h -> h.setBasicAuth("alice", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title",
                        "Meetup",
                        "venue",
                        "123 Main St",
                        "startsAt",
                        startsAt,
                        "endsAt",
                        endsAt,
                        "capacity",
                        capacity,
                        "guestLimit",
                        2))
                .exchange()
                .expectStatus()
                .isCreated());
    }

    private String event(String groupId, int capacity) {
        return event(groupId, STARTS, "2030-04-01T20:00:00Z", capacity);
    }

    private void rsvp(String member, String eventId, int guests) {
        client().put()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("guests", guests))
                .exchange()
                .expectStatus()
                .isOk();
    }

    private ResponseSpec mine(String member, String eventId) {
        return client().get()
                .uri("/events/" + eventId + "/rsvps/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .exchange();
    }

    private void leave(String member, String groupId) {
        client().delete()
                .uri("/groups/" + groupId + "/members/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void leavingWithdrawsConfirmedAndWaitlistedRsvpsToUpcomingEventsAndPromotes() {
        String groupId = groupWith("alice", "bob", "carol", "dave");
        String first = event(groupId, 2);
        String second = event(groupId, 1);
        rsvp("bob", first, 1);
        rsvp("carol", first, 0);
        rsvp("dave", first, 0);
        rsvp("carol", second, 0);
        rsvp("bob", second, 0);
        published.events.clear();

        leave("bob", groupId);

        mine("bob", first).expectStatus().isNotFound();
        mine("bob", second).expectStatus().isNotFound();
        mine("carol", first).expectBody().jsonPath("$.status").isEqualTo("confirmed");
        mine("dave", first).expectBody().jsonPath("$.status").isEqualTo("confirmed");
        mine("carol", second).expectBody().jsonPath("$.status").isEqualTo("confirmed");
        assertThat(published.events)
                .contains(
                        new RsvpWithdrawn(Long.parseLong(first), demoMember(2), 2),
                        new RsvpPromoted(Long.parseLong(first), demoMember(4), 1));
    }

    @Test
    void rsvpsToEventsThatHaveStartedAreUntouched() {
        String groupId = groupWith("alice", "bob");
        String event = event(groupId, 5);
        rsvp("bob", event, 0);
        NOW_REF.set(AFTER_START);

        leave("bob", groupId);

        mine("bob", event)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed");
    }

    @Test
    void leavingOneGroupLeavesRsvpsToAnotherGroupsEventsAlone() {
        String groupId = groupWith("alice", "bob");
        String otherGroup = groupWith("alice", "bob");
        String other = event(otherGroup, 5);
        rsvp("bob", other, 0);

        leave("bob", groupId);

        mine("bob", other).expectStatus().isOk();
    }

    /** The seeded demo member with this number: 1 alice, 2 bob, 3 carol, 4 dave. */
    private static UUID demoMember(int number) {
        return UUID.fromString("0197a000-0000-7000-8000-00000000000" + number);
    }
}
