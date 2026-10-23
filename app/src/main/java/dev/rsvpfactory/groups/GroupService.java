package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GroupService implements GroupAccess {

    private final GroupRepository groups;
    private final Members members;
    private final ApplicationEventPublisher events;

    GroupService(GroupRepository groups, Members members, ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
        this.events = events;
    }

    /** Starts a group; its creator is a member and its first organizer. */
    @Transactional
    GroupView start(Member creator, String name, String description) {
        UUID id = groups.insert(name, description);
        groups.addMember(id, creator.id(), true);
        return view(id);
    }

    /** Joins the group; joining a group the member already belongs to changes nothing. */
    @Transactional
    GroupView join(Member member, UUID id) {
        requireGroup(id);
        groups.join(id, member.id());
        return view(id);
    }

    /** Leaves the group and says so; leaving a group the member doesn't belong to changes nothing. */
    @Transactional
    void leave(Member member, UUID id) {
        requireGroup(id);
        if (groups.leave(id, member.id())) {
            events.publishEvent(new MemberLeftGroup(id, member.id()));
        }
    }

    /** Makes an existing member of the group an organizer; only an organizer may. */
    @Transactional
    GroupView makeOrganizer(Member by, UUID id, String memberId) {
        requireGroup(id);
        if (!groups.isOrganizer(id, by.id())) {
            throw RuleBrokenException.notAnOrganizer("make another member an organizer");
        }
        UUID organizerId = parse(memberId);
        if (organizerId == null || !groups.isMember(id, organizerId)) {
            throw new RuleBrokenException(
                    "not-a-member",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Only a member of the group can become an organizer.");
        }
        groups.makeOrganizer(id, organizerId);
        return view(id);
    }

    /** A malformed member id names nobody, like an unknown one. */
    private static @Nullable UUID parse(String memberId) {
        try {
            return UUID.fromString(memberId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    GroupView view(UUID id) {
        GroupRepository.GroupRow group = requireGroup(id);
        List<Member> organizers =
                List.copyOf(members.byIds(groups.organizerIds(id)).values());
        return new GroupView(group.id(), group.name(), group.description(), groups.memberCount(id), organizers);
    }

    private GroupRepository.GroupRow requireGroup(UUID id) {
        return groups.find(id).orElseThrow(() -> RuleBrokenException.notFound("group", id));
    }

    @Override
    public boolean exists(UUID groupId) {
        return groups.find(groupId).isPresent();
    }

    @Override
    public boolean isMember(UUID groupId, UUID memberId) {
        return groups.isMember(groupId, memberId);
    }

    @Override
    public boolean isOrganizer(UUID groupId, UUID memberId) {
        return groups.organizerIds(groupId).contains(memberId);
    }
}
