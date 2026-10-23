package dev.rsvpfactory.groups;

import jakarta.validation.constraints.NotBlank;

record MakeOrganizerRequest(@NotBlank String memberId) {}
