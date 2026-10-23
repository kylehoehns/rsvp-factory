package dev.rsvpfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The member table is ready for sign-up: ids come from the database and password hashes have room to grow. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberIdentityTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void aMemberInsertedWithoutAnIdGetsOneThatDoesNotCollideWithTheDemoMembers() {
        UUID id = jdbc.sql("INSERT INTO member (username, password_hash) VALUES ('erin', '{noop}x') RETURNING id")
                .query(UUID.class)
                .single();

        List<UUID> demoIds = jdbc.sql("SELECT id FROM member WHERE username IN ('alice', 'bob', 'carol', 'dave')")
                .query(UUID.class)
                .list();
        assertThat(demoIds).hasSize(4).doesNotContain(id);
    }

    @Test
    void aMemberInsertedWithoutAnIdGetsAUuidV7() {
        UUID id = jdbc.sql("INSERT INTO member (username, password_hash) VALUES ('gina', '{noop}x') RETURNING id")
                .query(UUID.class)
                .single();

        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void theDemoMembersHaveFixedIds() {
        assertThat(jdbc.sql(
                                "SELECT id FROM member WHERE username IN ('alice', 'bob', 'carol', 'dave') ORDER BY username")
                        .query(UUID.class)
                        .list())
                .containsExactly(
                        UUID.fromString("0197a000-0000-7000-8000-000000000001"),
                        UUID.fromString("0197a000-0000-7000-8000-000000000002"),
                        UUID.fromString("0197a000-0000-7000-8000-000000000003"),
                        UUID.fromString("0197a000-0000-7000-8000-000000000004"));
    }

    @Test
    void aPasswordHashLongerThanBcryptFits() {
        String argon2Sized = "{argon2@SpringSecurity_v5_8}" + "x".repeat(200);

        jdbc.sql("INSERT INTO member (username, password_hash) VALUES ('frank', :hash)")
                .param("hash", argon2Sized)
                .update();

        assertThat(jdbc.sql("SELECT password_hash FROM member WHERE username = 'frank'")
                        .query(String.class)
                        .single())
                .isEqualTo(argon2Sized);
    }
}
