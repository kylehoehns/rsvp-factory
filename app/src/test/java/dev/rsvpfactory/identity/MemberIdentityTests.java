package dev.rsvpfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rsvpfactory.TestcontainersConfiguration;
import java.util.List;
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
        long id = jdbc.sql("INSERT INTO member (username, password_hash) VALUES ('erin', '{noop}x') RETURNING id")
                .query(Long.class)
                .single();

        List<Long> demoIds = jdbc.sql("SELECT id FROM member WHERE username IN ('alice', 'bob', 'carol', 'dave')")
                .query(Long.class)
                .list();
        assertThat(demoIds).hasSize(4).doesNotContain(id);
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
