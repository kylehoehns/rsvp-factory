package dev.rsvpfactory.identity.internal;

import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/** The member table, seen as sign-in accounts and as the {@link Members} lookup. */
@Component
class MemberAccounts implements UserDetailsService, Members {

    private final JdbcClient jdbc;

    MemberAccounts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbc.sql("SELECT id, username, password_hash FROM member WHERE username = :username")
                .param("username", username)
                .query((rs, row) -> (UserDetails) new SignedInMember(
                        new Member(rs.getObject("id", UUID.class), rs.getString("username")),
                        rs.getString("password_hash")))
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("No member " + username));
    }

    @Override
    public Map<UUID, Member> byIds(Collection<UUID> ids) {
        Map<UUID, Member> members = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return members;
        }
        jdbc.sql("SELECT id, username FROM member WHERE id IN (:ids) ORDER BY username")
                .param("ids", ids)
                .query((rs, row) -> new Member(rs.getObject("id", UUID.class), rs.getString("username")))
                .list()
                .forEach(member -> members.put(member.id(), member));
        return members;
    }
}
