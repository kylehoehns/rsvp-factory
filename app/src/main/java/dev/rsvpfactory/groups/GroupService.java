package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import dev.rsvpfactory.problems.RuleBrokenException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GroupService {

    private final GroupRepository groups;
    private final Members members;

    GroupService(GroupRepository groups, Members members) {
        this.groups = groups;
        this.members = members;
    }

    /** Starts a group; its creator is a member and its first organizer. */
    @Transactional
    GroupView start(Member creator, String name, String description) {
        long id = groups.insert(name, description);
        groups.addMember(id, creator.id(), true);
        return view(id);
    }

    GroupView view(long id) {
        GroupRepository.GroupRow group = groups.find(id)
                .orElseThrow(() -> new RuleBrokenException(
                        "group-not-found", HttpStatus.NOT_FOUND, "There is no group " + id + "."));
        List<Member> organizers =
                List.copyOf(members.byIds(groups.organizerIds(id)).values());
        return new GroupView(group.id(), group.name(), group.description(), organizers);
    }
}
