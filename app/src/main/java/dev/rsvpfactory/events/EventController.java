package dev.rsvpfactory.events;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.problems.RuleBrokenException;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventController {

    private final EventService events;
    private final CurrentMember currentMember;

    EventController(EventService events, CurrentMember currentMember) {
        this.events = events;
        this.currentMember = currentMember;
    }

    @PostMapping("/groups/{groupId}/events")
    ResponseEntity<EventDetails> schedule(
            @PathVariable String groupId, @Valid @RequestBody ScheduleEventRequest request) {
        long id;
        try {
            id = Long.parseLong(groupId);
        } catch (NumberFormatException e) {
            throw new RuleBrokenException(
                    "group-not-found", HttpStatus.NOT_FOUND, "There is no group " + groupId + ".");
        }
        EventDetails event = events.schedule(currentMember.get(), id, request);
        return ResponseEntity.created(URI.create("/events/" + event.id())).body(event);
    }
}
