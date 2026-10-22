package dev.rsvpfactory.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** The request to start a group. */
public record StartGroup(
        @NotBlank @Size(max = 100) String name,
        @Nullable @Size(max = 2000) String description) {}
