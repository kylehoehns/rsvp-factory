package dev.rsvpfactory.attendance;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CheckInController {

    private final CheckInService checkIns;
    private final CurrentMember currentMember;

    CheckInController(CheckInService checkIns, CurrentMember currentMember) {
        this.checkIns = checkIns;
        this.currentMember = currentMember;
    }

    @PutMapping("/events/{eventId}/check-ins/{memberId}")
    CheckInView checkIn(
            @PathVariable String eventId,
            @PathVariable String memberId,
            @RequestBody(required = false) Optional<CheckInRequest> request) {
        return checkIns.checkIn(
                currentMember.get(),
                parse("event", eventId),
                parse("member", memberId),
                request.map(CheckInRequest::arrived).orElse(null));
    }

    @DeleteMapping("/events/{eventId}/check-ins/{memberId}")
    ResponseEntity<Void> undo(@PathVariable String eventId, @PathVariable String memberId) {
        checkIns.undo(currentMember.get(), parse("event", eventId), parse("member", memberId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events/{eventId}/check-ins/me")
    CheckInView mine(@PathVariable String eventId) {
        return checkIns.mine(currentMember.get(), parse("event", eventId));
    }

    private static UUID parse(String what, String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw RuleBrokenException.notFound(what, id);
        }
    }
}
