package dev.rsvpfactory.identity.internal;

import dev.rsvpfactory.identity.Member;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
class MemberUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbc;

    MemberUserDetailsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbc.sql("SELECT id, username, name, password_hash FROM members WHERE username = :username")
                .param("username", username)
                .query((rs, rowNum) -> new MemberPrincipal(
                        new Member(rs.getLong("id"), rs.getString("username"), rs.getString("name")),
                        rs.getString("password_hash")))
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("No member " + username));
    }
}
