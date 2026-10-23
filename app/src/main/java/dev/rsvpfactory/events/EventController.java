package dev.rsvpfactory.events;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.problems.RuleBrokenException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
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
        UUID id;
        try {
            id = UUID.fromString(groupId);
        } catch (IllegalArgumentException e) {
            throw RuleBrokenException.notFound("group", groupId);
        }
        EventDetails event = events.schedule(currentMember.get(), id, request);
        return ResponseEntity.created(URI.create("/events/" + event.id())).body(event);
    }

    @PostMapping("/events/{eventId}/cancellation")
    EventDetails cancel(@PathVariable String eventId) {
        long id;
        try {
            id = Long.parseLong(eventId);
        } catch (NumberFormatException e) {
            throw RuleBrokenException.notFound("event", eventId);
        }
        return events.cancel(currentMember.get(), id);
    }
}
