package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.problems.RuleBrokenException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RsvpController {

    private final RsvpService rsvps;
    private final CurrentMember currentMember;

    RsvpController(RsvpService rsvps, CurrentMember currentMember) {
        this.rsvps = rsvps;
        this.currentMember = currentMember;
    }

    @PutMapping("/events/{eventId}/rsvps/me")
    RsvpView rsvp(@PathVariable String eventId, @Valid @RequestBody RsvpRequest request) {
        return rsvps.rsvp(currentMember.get(), parse(eventId), request.guestCount());
    }

    @PutMapping("/events/{eventId}/capacity")
    EventSeatsView changeCapacity(@PathVariable String eventId, @Valid @RequestBody CapacityRequest request) {
        return rsvps.changeCapacity(currentMember.get(), parse(eventId), request.capacity());
    }

    @GetMapping("/events/{eventId}/rsvps/me")
    RsvpView mine(@PathVariable String eventId) {
        return rsvps.mine(currentMember.get(), parse(eventId));
    }

    @DeleteMapping("/events/{eventId}/rsvps/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable String eventId) {
        rsvps.withdraw(currentMember.get(), parse(eventId));
    }

    private static long parse(String eventId) {
        try {
            return Long.parseLong(eventId);
        } catch (NumberFormatException e) {
            throw RuleBrokenException.notFound("event", eventId);
        }
    }
}
