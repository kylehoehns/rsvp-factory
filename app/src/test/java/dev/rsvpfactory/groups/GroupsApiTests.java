package dev.rsvpfactory.groups;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Seam A for signing in and starting a group: the HTTP API against a real Postgres, as the seeded demo members. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GroupsApiTests {

    private static final String PASSWORD = "rsvp-demo";

    @Value("${local.server.port}")
    int port;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private ResponseSpec startGroup(String member, Object body) {
        return client().post()
                .uri("/groups")
                .headers(h -> h.setBasicAuth(member, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private ResponseSpec viewGroup(String groupId) {
        return client().get().uri("/groups/" + groupId).exchange();
    }

    private static void assertProblem(ResponseSpec response, int status, String rule) {
        response.expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://rsvp-factory.dev/problems/" + rule)
                .jsonPath("$.status")
                .isEqualTo(status);
    }

    private String startedGroupId(String member) {
        String[] id = new String[1];
        startGroup(member, Map.of("name", "Chess club", "description", "Weekly games"))
                .expectBody()
                .jsonPath("$.id")
                .value(String.class, value -> id[0] = value);
        return Objects.requireNonNull(id[0]);
    }

    @Test
    void aSeededMemberStartsAGroupAndBecomesItsOrganizer() {
        startGroup("alice", Map.of("name", "Chess club", "description", "Weekly games"))
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists(HttpHeaders.LOCATION)
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Chess club")
                .jsonPath("$.description")
                .isEqualTo("Weekly games")
                .jsonPath("$.organizers.length()")
                .isEqualTo(1)
                .jsonPath("$.organizers[0].username")
                .isEqualTo("alice");
    }

    @Test
    void aGroupWithoutADescriptionHasAnEmptyOne() {
        startGroup("bob", Map.of("name", "Quiet club"))
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.description")
                .isEqualTo("");
    }

    @Test
    void anyoneCanViewAGroupWithoutSigningIn() {
        String groupId = startedGroupId("carol");

        viewGroup(groupId)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(groupId)
                .jsonPath("$.name")
                .isEqualTo("Chess club")
                .jsonPath("$.description")
                .isEqualTo("Weekly games")
                .jsonPath("$.organizers[0].username")
                .isEqualTo("carol");
    }

    @Test
    void startingAGroupWithoutSigningInIsRefusedAsNotAuthenticated() {
        ResponseSpec response = client().post()
                .uri("/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Chess club"))
                .exchange();

        assertProblem(response, 401, "not-authenticated");
        response.expectHeader()
                .value(
                        HttpHeaders.WWW_AUTHENTICATE,
                        challenge -> assertThat(challenge).startsWith("Basic"));
    }

    @Test
    void aWrongPasswordIsRefusedAsNotAuthenticated() {
        ResponseSpec response = client().post()
                .uri("/groups")
                .headers(h -> h.setBasicAuth("alice", "not-the-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Chess club"))
                .exchange();

        assertProblem(response, 401, "not-authenticated");
    }

    @Test
    void anUnknownMemberIsRefusedAsNotAuthenticated() {
        ResponseSpec response = client().post()
                .uri("/groups")
                .headers(h -> h.setBasicAuth("mallory", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Chess club"))
                .exchange();

        assertProblem(response, 401, "not-authenticated");
    }

    @Test
    void aBlankGroupNameIsAValidationProblem() {
        ResponseSpec response = startGroup("alice", Map.of("name", "  ", "description", "x"));

        assertProblem(response, 400, "validation-failed");
        response.expectBody()
                .jsonPath("$.errors[0]")
                .value(String.class, error -> assertThat(error).startsWith("name "));
    }

    @Test
    void aMissingGroupNameIsAValidationProblem() {
        assertProblem(startGroup("alice", Map.of("description", "x")), 400, "validation-failed");
    }

    @Test
    void aTooLongGroupNameIsAValidationProblem() {
        assertProblem(startGroup("alice", Map.of("name", "x".repeat(101))), 400, "validation-failed");
    }

    @Test
    void aTooLongDescriptionIsAValidationProblem() {
        assertProblem(
                startGroup("alice", Map.of("name", "ok", "description", "x".repeat(2001))), 400, "validation-failed");
    }

    @Test
    void aMalformedBodyIsABadRequestProblem() {
        ResponseSpec response = client().post()
                .uri("/groups")
                .headers(h -> h.setBasicAuth("alice", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not json")
                .exchange();

        assertProblem(response, 400, "bad-request");
    }

    @Test
    void anUnknownGroupIsNotFound() {
        assertProblem(viewGroup("999999"), 404, "group-not-found");
    }

    @Test
    void aGroupIdThatIsNotAnIdIsNotFound() {
        assertProblem(viewGroup("not-an-id"), 404, "group-not-found");
    }

    @Test
    void theHealthEndpointIsPublic() {
        client().get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void otherActuatorEndpointsNeedSigningIn() {
        assertProblem(client().get().uri("/actuator/info").exchange(), 401, "not-authenticated");
    }

    @Test
    void unlistedRoutesNeedSigningIn() {
        assertProblem(client().get().uri("/groups").exchange(), 401, "not-authenticated");
    }
}
