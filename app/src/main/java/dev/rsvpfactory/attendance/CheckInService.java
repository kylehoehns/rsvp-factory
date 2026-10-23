package dev.rsvpfactory.attendance;

import dev.rsvpfactory.events.EventDetails;
import dev.rsvpfactory.events.EventStatus;
import dev.rsvpfactory.events.Events;
import dev.rsvpfactory.groups.GroupAccess;
import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.problems.RuleBrokenException;
import dev.rsvpfactory.rsvp.Rsvps;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CheckInService {

    /** Check-in opens this long before the event starts. */
    static final Duration OPENS_BEFORE_START = Duration.ofHours(1);

    private final Events events;
    private final GroupAccess groups;
    private final Rsvps rsvps;
    private final CheckInRepository checkIns;
    private final Clock clock;

    CheckInService(Events events, GroupAccess groups, Rsvps rsvps, CheckInRepository checkIns, Clock clock) {
        this.events = events;
        this.groups = groups;
        this.rsvps = rsvps;
        this.checkIns = checkIns;
        this.clock = clock;
    }

    /** Records that {@code arrived} of the member's confirmed party came (the whole party if null), or corrects it. */
    @Transactional
    CheckInView checkIn(Member organizer, UUID eventId, UUID memberId, @Nullable Integer arrived) {
        EventDetails event = events.find(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        requireOrganizer(organizer, event, "check members in");
        if (arrived != null && arrived < 1) {
            throw unprocessable("arrived-must-be-positive", "At least one person must arrive.");
        }
        Instant now = Instant.now(clock);
        requireOpen(event, now);
        int party = rsvps.confirmedPartySize(eventId, memberId)
                .orElseThrow(() ->
                        unprocessable("rsvp-not-confirmed", "Only a member with a confirmed RSVP can be checked in."));
        int count = arrived == null ? party : arrived;
        if (count > party) {
            throw unprocessable("arrived-exceeds-party", "More people arrived than the party of " + party + ".");
        }
        return checkIns.save(eventId, memberId, count, now);
    }

    /** Removes the member's check-in while check-in is open. */
    @Transactional
    void undo(Member organizer, UUID eventId, UUID memberId) {
        EventDetails event = events.find(eventId).orElseThrow(() -> RuleBrokenException.notFound("event", eventId));
        requireOrganizer(organizer, event, "undo check-ins");
        requireOpen(event, Instant.now(clock));
        if (!checkIns.delete(eventId, memberId)) {
            throw RuleBrokenException.notFound("check-in", eventId);
        }
    }

    private void requireOrganizer(Member organizer, EventDetails event, String action) {
        if (!groups.isOrganizer(event.groupId(), organizer.id())) {
            throw RuleBrokenException.notAnOrganizer(action);
        }
    }

    private static void requireOpen(EventDetails event, Instant now) {
        if (event.status() == EventStatus.CANCELLED
                || now.isBefore(event.startsAt().minus(OPENS_BEFORE_START))
                || !now.isBefore(event.endsAt())) {
            throw unprocessable(
                    "check-in-closed",
                    "Check-in is open from an hour before the event starts until it ends, and never for a cancelled"
                            + " event.");
        }
    }

    /** The member's own check-in at the event. */
    @Transactional(readOnly = true)
    CheckInView mine(Member member, UUID eventId) {
        if (events.find(eventId).isEmpty()) {
            throw RuleBrokenException.notFound("event", eventId);
        }
        return checkIns.find(eventId, member.id()).orElseThrow(() -> RuleBrokenException.notFound("check-in", eventId));
    }

    private static RuleBrokenException unprocessable(String rule, String message) {
        return new RuleBrokenException(rule, HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
