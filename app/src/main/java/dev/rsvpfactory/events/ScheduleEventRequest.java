package dev.rsvpfactory.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record ScheduleEventRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) @Nullable String description,
        @NotBlank @Size(max = 500) String venue,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Integer capacity,
        @Nullable Integer guestLimit,
        @Nullable Instant rsvpCutoff) {}
