package dev.rsvpfactory.identity.security;

import dev.rsvpfactory.identity.Member;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** A signed-in member as Spring Security sees them. */
public record MemberPrincipal(Member member, String passwordHash) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return member.name();
    }
}
