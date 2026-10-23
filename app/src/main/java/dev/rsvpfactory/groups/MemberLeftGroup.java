package dev.rsvpfactory.groups;

import java.util.UUID;

/** Published when a member leaves a group, for modules that hold things on the member's behalf in it. */
public record MemberLeftGroup(long groupId, UUID memberId) {}
