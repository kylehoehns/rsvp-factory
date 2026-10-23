package dev.rsvpfactory.groups;

import java.util.UUID;

/** What other modules may ask about a group. */
public interface GroupAccess {

    boolean exists(long groupId);

    boolean isMember(long groupId, UUID memberId);

    boolean isOrganizer(long groupId, UUID memberId);
}
