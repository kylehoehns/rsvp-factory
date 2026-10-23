package dev.rsvpfactory.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for changing the guests of an RSVP: the HTTP API against a real Postgres. */
@Import({TestcontainersConfiguration.class, RsvpApiTests.Setup.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestChangeApiTests {

    @Value("${local.server.port}")
    int port;

    @Autowired
    RsvpApiTests.Setup.Published published;

    private RsvpApi api() {
        return new RsvpApi(port);
    }

    private String groupWith(String organizer, String... members) {
        return api().groupWith(organizer, members);
    }

    private String event(String groupId, int capacity, int guestLimit) {
        return api().event(groupId, capacity, guestLimit);
    }

    private ResponseSpec rsvp(String member, String eventId, Object guests) {
        return api().rsvp(member, eventId, guests);
    }

    private ResponseSpec mine(String member, String eventId) {
        return api().mine(member, eventId);
    }

    private void assertOpenSeats(String eventId, int openSeats, int waitlistLength) {
        api().assertOpenSeats(eventId, openSeats, waitlistLength);
    }

    private static void assertRefused(ResponseSpec response, int status, String rule) {
        RsvpApi.assertRefused(response, status, rule);
    }

    @Test
    void aConfirmedRsvpAddsGuestsWhenSeatsAreOpen() {
        String eventId = event(groupWith("alice", "bob"), 10, 2);
        rsvp("bob", eventId, 0).expectStatus().isOk();

        rsvp("bob", eventId, 2)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(3);

        mine("bob", eventId).expectBody().jsonPath("$.partySize").isEqualTo(3);
        assertOpenSeats(eventId, 7, 0);
    }

    @Test
    void addingGuestsWithoutEnoughOpenSeatsIsRefusedAndTheRsvpStaysConfirmed() {
        String eventId = event(groupWith("alice", "bob", "carol"), 3, 2);
        rsvp("bob", eventId, 0).expectStatus().isOk();
        rsvp("carol", eventId, 1).expectStatus().isOk();

        assertRefused(rsvp("bob", eventId, 1), 409, "not-enough-open-seats");

        mine("bob", eventId)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(1);
        assertOpenSeats(eventId, 0, 0);
    }

    @Test
    void removingGuestsFreesSeatsAndPromotesTheWaitlist() {
        String eventId = event(groupWith("alice", "bob", "carol", "dave"), 4, 3);
        rsvp("bob", eventId, 3).expectStatus().isOk();
        rsvp("carol", eventId, 0).expectStatus().isOk();
        rsvp("dave", eventId, 0).expectStatus().isOk();

        rsvp("bob", eventId, 1)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("confirmed")
                .jsonPath("$.partySize")
                .isEqualTo(2);

        mine("carol", eventId).expectBody().jsonPath("$.status").isEqualTo("confirmed");
        mine("dave", eventId).expectBody().jsonPath("$.status").isEqualTo("confirmed");
        assertOpenSeats(eventId, 0, 0);
    }

    @Test
    void changingGuestsOnAWaitlistedRsvpKeepsItsPlaceInLine() {
        String eventId = event(groupWith("alice", "bob", "carol", "dave"), 2, 2);
        rsvp("bob", eventId, 1).expectStatus().isOk();
        rsvp("carol", eventId, 0).expectStatus().isOk();
        rsvp("dave", eventId, 0).expectStatus().isOk();

        rsvp("carol", eventId, 2)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("waitlisted")
                .jsonPath("$.partySize")
                .isEqualTo(3)
                .jsonPath("$.waitlistPosition")
                .isEqualTo(1);

        mine("dave", eventId).expectBody().jsonPath("$.waitlistPosition").isEqualTo(2);
        assertOpenSeats(eventId, 0, 2);
    }

    @Test
    void changingGuestsIsHeldToTheGuestLimit() {
        String eventId = event(groupWith("alice", "bob"), 10, 1);
        rsvp("bob", eventId, 0).expectStatus().isOk();

        assertRefused(rsvp("bob", eventId, 2), 422, "guest-limit-exceeded");
        assertOpenSeats(eventId, 9, 0);
    }

    @Test
    void guestChangesPublishPromotionsAsEvents() {
        String eventId = event(groupWith("alice", "bob", "carol"), 2, 1);
        rsvp("bob", eventId, 1).expectStatus().isOk();
        rsvp("carol", eventId, 0).expectStatus().isOk();
        UUID event = UUID.fromString(eventId);

        rsvp("bob", eventId, 0).expectStatus().isOk();

        assertThat(published.events)
                .anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(RsvpPromoted.class, p -> {
                    assertThat(p.eventId()).isEqualTo(event);
                    assertThat(p.partySize()).isEqualTo(1);
                }));
    }
}
