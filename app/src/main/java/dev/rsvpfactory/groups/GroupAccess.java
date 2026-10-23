package dev.rsvpfactory.groups;

import java.util.UUID;

/** What other modules may ask about a group. */
public interface GroupAccess {

    boolean exists(UUID groupId);

    boolean isMember(UUID groupId, UUID memberId);

    boolean isOrganizer(UUID groupId, UUID memberId);
}
