package dev.rsvpfactory.groups;

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
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for joining, leaving and organizers: the HTTP API against a real Postgres, as the seeded demo members. */
@Import({TestcontainersConfiguration.class, MembershipApiTests.LeftGroupEvents.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MembershipApiTests {

    private static final String PASSWORD = "rsvp-demo";
    // Seeded by R__demo_members.sql; ids are looked up from the group view where possible.
    private static final long BOB = 2;

    @Value("${local.server.port}")
    int port;

    @Autowired
    LeftGroupEvents leftGroup;

    /** Collects the events the application publishes; the request runs on a server thread, not the test's. */
    static class LeftGroupEvents {
        final List<MemberLeftGroup> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(MemberLeftGroup event) {
            seen.add(event);
        }
    }

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
                .value(String.class, value -> id[0] = value);
        return Objects.requireNonNull(id[0]);
    }

    private ResponseSpec join(String member, String groupId) {
        return client().put()
                .uri("/groups/" + groupId + "/members/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .exchange();
    }

    private ResponseSpec leave(String member, String groupId) {
        return client().delete()
                .uri("/groups/" + groupId + "/members/me")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .exchange();
    }

    private ResponseSpec makeOrganizer(String by, String groupId, Object body) {
        return client().post()
                .uri("/groups/" + groupId + "/organizers")
                .headers(h -> h.setBasicAuth(by, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private ResponseSpec view(String groupId) {
        return client().get().uri("/groups/" + groupId).exchange();
    }

    private static void assertProblem(ResponseSpec response, int status, String rule) {
        response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://rsvp-factory.dev/problems/" + rule);
    }

    @Test
    void aNewGroupHasOneMember() {
        view(startGroup("alice")).expectBody().jsonPath("$.memberCount").isEqualTo(1);
    }

    @Test
    void joiningAddsAMemberAndRepeatingItIsHarmless() {
        String groupId = startGroup("alice");

        join("bob", groupId).expectStatus().isOk();
        join("bob", groupId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.memberCount")
                .isEqualTo(2);
        view(groupId).expectBody().jsonPath("$.memberCount").isEqualTo(2);
    }

    @Test
    void joiningAnUnknownGroupIsNotFound() {
        assertProblem(join("bob", "999999"), 404, "group-not-found");
    }

    @Test
    void joiningWithoutSigningInIsRefused() {
        String groupId = startGroup("alice");
        assertProblem(client().put().uri("/groups/" + groupId + "/members/me").exchange(), 401, "not-authenticated");
    }

    @Test
    void leavingRemovesTheMemberAndPublishesAnEventSayingSo() {
        String groupId = startGroup("alice");
        join("bob", groupId);

        leave("bob", groupId).expectStatus().isNoContent();

        view(groupId).expectBody().jsonPath("$.memberCount").isEqualTo(1);
        assertThat(leftGroup.seen.stream()).containsExactly(new MemberLeftGroup(Long.parseLong(groupId), BOB));
    }

    @Test
    void leavingAGroupYouNeverJoinedChangesNothingAndPublishesNothing() {
        String groupId = startGroup("alice");

        leave("carol", groupId).expectStatus().isNoContent();

        view(groupId).expectBody().jsonPath("$.memberCount").isEqualTo(1);
        assertThat(leftGroup.seen.stream().filter(e -> e.groupId() == Long.parseLong(groupId)))
                .isEmpty();
    }

    @Test
    void leavingAnUnknownGroupIsNotFound() {
        assertProblem(leave("bob", "999999"), 404, "group-not-found");
    }

    @Test
    void anOrganizerMakesAnExistingMemberAnOrganizer() {
        String groupId = startGroup("alice");
        join("bob", groupId);

        makeOrganizer("alice", groupId, Map.of("memberId", BOB))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.organizers.length()")
                .isEqualTo(2);

        // The new organizer can now do the same.
        join("carol", groupId);
        makeOrganizer("bob", groupId, Map.of("memberId", 3)).expectStatus().isOk();
        view(groupId).expectBody().jsonPath("$.organizers.length()").isEqualTo(3);
    }

    @Test
    void makingAnOrganizerTwiceIsHarmless() {
        String groupId = startGroup("alice");
        join("bob", groupId);
        makeOrganizer("alice", groupId, Map.of("memberId", BOB)).expectStatus().isOk();

        makeOrganizer("alice", groupId, Map.of("memberId", BOB))
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.organizers.length()")
                .isEqualTo(2);
    }

    @Test
    void aNonMemberCannotBecomeAnOrganizer() {
        String groupId = startGroup("alice");

        assertProblem(makeOrganizer("alice", groupId, Map.of("memberId", BOB)), 422, "not-a-member");
        assertProblem(makeOrganizer("alice", groupId, Map.of("memberId", 999999)), 422, "not-a-member");
        view(groupId).expectBody().jsonPath("$.organizers.length()").isEqualTo(1);
    }

    @Test
    void aMemberWhoIsNotAnOrganizerCannotMakeOne() {
        String groupId = startGroup("alice");
        join("bob", groupId);
        join("carol", groupId);

        assertProblem(makeOrganizer("bob", groupId, Map.of("memberId", 3)), 403, "not-an-organizer");
        view(groupId).expectBody().jsonPath("$.organizers.length()").isEqualTo(1);
    }

    @Test
    void aStrangerCannotMakeAnOrganizer() {
        String groupId = startGroup("alice");
        join("bob", groupId);

        assertProblem(makeOrganizer("dave", groupId, Map.of("memberId", BOB)), 403, "not-an-organizer");
    }

    @Test
    void makingAnOrganizerNeedsAMemberId() {
        String groupId = startGroup("alice");

        assertProblem(makeOrganizer("alice", groupId, Map.of()), 400, "validation-failed");
    }

    @Test
    void makingAnOrganizerInAnUnknownGroupIsNotFound() {
        assertProblem(makeOrganizer("alice", "999999", Map.of("memberId", BOB)), 404, "group-not-found");
    }

    @Test
    void theGroupViewListsMemberCountAndOrganizers() {
        String groupId = startGroup("alice");
        join("bob", groupId);
        join("carol", groupId);
        makeOrganizer("alice", groupId, Map.of("memberId", BOB));

        view(groupId)
                .expectBody()
                .jsonPath("$.memberCount")
                .isEqualTo(3)
                .jsonPath("$.organizers[0].username")
                .isEqualTo("alice")
                .jsonPath("$.organizers[1].username")
                .isEqualTo("bob");
    }
}
