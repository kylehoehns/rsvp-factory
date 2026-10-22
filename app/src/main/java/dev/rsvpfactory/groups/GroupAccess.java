package dev.rsvpfactory.groups;

/** What other modules may ask about a group. */
public interface GroupAccess {

    boolean exists(long groupId);

    boolean isMember(long groupId, long memberId);

    boolean isOrganizer(long groupId, long memberId);
}
