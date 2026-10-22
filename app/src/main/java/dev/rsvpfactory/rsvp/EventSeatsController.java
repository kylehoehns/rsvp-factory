package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.events.EventDetails;
import dev.rsvpfactory.events.Events;
import dev.rsvpfactory.groups.GroupAccess;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public reads of an event: its details from {@code events} together with the seats {@code rsvp} owns. They live
 * here because {@code events} cannot depend on {@code rsvp}, which already listens to it.
 */
@RestController
class EventSeatsController {

    private final Events events;
    private final GroupAccess groups;
    private final SeatingPlanRepository plans;

    EventSeatsController(Events events, GroupAccess groups, SeatingPlanRepository plans) {
        this.events = events;
        this.groups = groups;
        this.plans = plans;
    }

    @GetMapping("/events/{eventId}")
    EventSeatsView view(@PathVariable String eventId) {
        long id = parse(eventId, "event-not-found", "event");
        EventDetails event = events.find(id).orElseThrow(() -> notFound("event-not-found", "event", eventId));
        SeatingPlan plan = plans.find(id).orElseThrow(() -> notFound("event-not-found", "event", eventId));
        return EventSeatsView.of(event, plan);
    }

    @GetMapping("/groups/{groupId}/events")
    List<EventSeatsView> inGroup(@PathVariable String groupId) {
        long id = parse(groupId, "group-not-found", "group");
        if (!groups.exists(id)) {
            throw notFound("group-not-found", "group", groupId);
        }
        List<EventDetails> inGroup = events.inGroup(id);
        Map<Long, SeatingPlan> byEvent =
                plans.byEventIds(inGroup.stream().map(EventDetails::id).toList());
        return inGroup.stream()
                .flatMap(event ->
                        Optional.ofNullable(byEvent.get(event.id()))
                                .map(plan -> EventSeatsView.of(event, plan))
                                .stream())
                .toList();
    }

    private static long parse(String id, String rule, String what) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw notFound(rule, what, id);
        }
    }

    private static RuleBrokenException notFound(String rule, String what, String id) {
        return new RuleBrokenException(rule, HttpStatus.NOT_FOUND, "There is no " + what + " " + id + ".");
    }
}
