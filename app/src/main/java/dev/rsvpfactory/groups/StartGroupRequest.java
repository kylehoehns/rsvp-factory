package dev.rsvpfactory.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

record StartGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) @Nullable String description) {}
