package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.util.List;
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
        long id = groups.insert(name, description);
        groups.addMember(id, creator.id(), true);
        return view(id);
    }

    /** Joins the group; joining a group the member already belongs to changes nothing. */
    @Transactional
    GroupView join(Member member, long id) {
        requireGroup(id);
        groups.join(id, member.id());
        return view(id);
    }

    /** Leaves the group and says so; leaving a group the member doesn't belong to changes nothing. */
    @Transactional
    void leave(Member member, long id) {
        requireGroup(id);
        if (groups.leave(id, member.id())) {
            events.publishEvent(new MemberLeftGroup(id, member.id()));
        }
    }

    /** Makes an existing member of the group an organizer; only an organizer may. */
    @Transactional
    GroupView makeOrganizer(Member by, long id, long memberId) {
        requireGroup(id);
        if (!groups.isOrganizer(id, by.id())) {
            throw RuleBrokenException.notAnOrganizer("make another member an organizer");
        }
        if (!groups.isMember(id, memberId)) {
            throw new RuleBrokenException(
                    "not-a-member",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Only a member of the group can become an organizer.");
        }
        groups.makeOrganizer(id, memberId);
        return view(id);
    }

    GroupView view(long id) {
        GroupRepository.GroupRow group = requireGroup(id);
        List<Member> organizers =
                List.copyOf(members.byIds(groups.organizerIds(id)).values());
        return new GroupView(group.id(), group.name(), group.description(), groups.memberCount(id), organizers);
    }

    private GroupRepository.GroupRow requireGroup(long id) {
        return groups.find(id).orElseThrow(() -> RuleBrokenException.notFound("group", id));
    }

    @Override
    public boolean exists(long groupId) {
        return groups.find(groupId).isPresent();
    }

    @Override
    public boolean isMember(long groupId, long memberId) {
        return groups.isMember(groupId, memberId);
    }

    @Override
    public boolean isOrganizer(long groupId, long memberId) {
        return groups.organizerIds(groupId).contains(memberId);
    }
}
