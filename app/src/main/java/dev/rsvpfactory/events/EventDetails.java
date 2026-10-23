package dev.rsvpfactory.events;

import java.time.Instant;
import java.util.UUID;

/** An event's what and when, as other modules see it. */
public record EventDetails(
        long id,
        UUID groupId,
        String title,
        String description,
        String venue,
        Instant startsAt,
        Instant endsAt,
        EventStatus status) {}
