package dev.rsvpfactory.identity;

import dev.rsvpfactory.identity.internal.MemberPrincipal;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The identity module's API: who is signed in, and who a member id belongs to. */
@Component
public class Members {

    private final JdbcClient jdbc;

    Members(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The member making the current request. Only call this from an endpoint that requires sign-in. */
    public Member current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.member();
        }
        throw new IllegalStateException("No signed-in member on this request");
    }

    /** The members with these ids, ordered by id. */
    public List<Member> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, username, name FROM members WHERE id IN (:ids) ORDER BY id")
                .param("ids", ids)
                .query((rs, rowNum) -> new Member(rs.getLong("id"), rs.getString("username"), rs.getString("name")))
                .list();
    }
}
