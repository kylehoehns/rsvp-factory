package dev.rsvpfactory.identity.internal;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.identity.Member;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SecurityContextCurrentMember implements CurrentMember {

    @Override
    public Member get() {
        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof SignedInMember signedIn) {
            return signedIn.member();
        }
        throw new IllegalStateException("No member is signed in");
    }
}
