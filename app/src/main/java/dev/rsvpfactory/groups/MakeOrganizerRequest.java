package dev.rsvpfactory.groups;

import jakarta.validation.constraints.NotNull;

record MakeOrganizerRequest(@NotNull Long memberId) {}
