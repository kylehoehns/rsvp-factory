package dev.rsvpfactory.identity;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Looks members up by id, for modules that refer to members by id only. */
@Component
public class Members {

    private final JdbcClient jdbc;

    Members(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Member> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, username FROM member WHERE id IN (:ids) ORDER BY username")
                .param("ids", ids)
                .query((rs, row) -> new Member(rs.getLong("id"), rs.getString("username")))
                .list();
    }
}
