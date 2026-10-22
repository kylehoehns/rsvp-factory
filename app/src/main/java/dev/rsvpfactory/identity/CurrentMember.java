package dev.rsvpfactory.identity;

import dev.rsvpfactory.identity.security.MemberPrincipal;
import dev.rsvpfactory.problems.Refusal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The member making the current request. */
@Component
public class CurrentMember {

    /** The signed-in member, or an {@code unauthenticated} refusal when nobody is signed in. */
    public Member require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.member();
        }
        throw new Refusal(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to do this.");
    }
}
