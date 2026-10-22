package dev.rsvpfactory.rsvp;

import dev.rsvpfactory.events.EventDetails;
import dev.rsvpfactory.events.Events;
import dev.rsvpfactory.groups.GroupAccess;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final RsvpRepository rsvps;

    EventSeatsController(Events events, GroupAccess groups, SeatingPlanRepository plans, RsvpRepository rsvps) {
        this.events = events;
        this.groups = groups;
        this.plans = plans;
        this.rsvps = rsvps;
    }

    @GetMapping("/events/{eventId}")
    EventSeatsView view(@PathVariable String eventId) {
        long id = parse(eventId, "event");
        EventDetails event = events.find(id).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        SeatingPlan plan = plans.find(id).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        return EventSeatsView.of(event, plan, rsvps.seats(List.of(id)).getOrDefault(id, RsvpRepository.Seats.NONE));
    }

    @GetMapping("/groups/{groupId}/events")
    List<EventSeatsView> inGroup(@PathVariable String groupId) {
        long id = parse(groupId, "group");
        if (!groups.exists(id)) {
            throw RuleBrokenException.notFound("group", groupId);
        }
        List<EventDetails> inGroup = events.inGroup(id);
        List<Long> ids = inGroup.stream().map(EventDetails::id).toList();
        Map<Long, SeatingPlan> byEvent = plans.byEventIds(ids);
        Map<Long, RsvpRepository.Seats> seats = rsvps.seats(ids);
        return inGroup.stream()
                .flatMap(event -> Optional.ofNullable(byEvent.get(event.id()))
                        .map(plan -> EventSeatsView.of(
                                event, plan, seats.getOrDefault(event.id(), RsvpRepository.Seats.NONE)))
                        .stream())
                .toList();
    }

    private static long parse(String id, String what) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw RuleBrokenException.notFound(what, id);
        }
    }
}
