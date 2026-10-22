package dev.rsvpfactory.groups;

/** Published when a member leaves a group, for modules that hold things on the member's behalf in it. */
public record MemberLeftGroup(long groupId, long memberId) {}
