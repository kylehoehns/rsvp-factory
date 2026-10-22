package dev.rsvpfactory.groups.internal;

import dev.rsvpfactory.groups.Group;
import dev.rsvpfactory.groups.Groups;
import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GroupsController {

    private final Groups groups;
    private final Members members;

    GroupsController(Groups groups, Members members) {
        this.groups = groups;
        this.members = members;
    }

    record StartGroupRequest(@NotBlank String name, String description) {}

    record OrganizerResponse(long id, String name) {}

    record GroupResponse(long id, String name, String description, List<OrganizerResponse> organizers) {

        static GroupResponse of(Group group) {
            return new GroupResponse(
                    group.id(),
                    group.name(),
                    group.description(),
                    group.organizers().stream()
                            .map(member -> new OrganizerResponse(member.id(), member.name()))
                            .toList());
        }
    }

    @PostMapping("/groups")
    ResponseEntity<GroupResponse> start(@Valid @RequestBody StartGroupRequest request) {
        Member founder = members.current();
        Group group = groups.start(
                request.name().strip(), request.description() == null ? "" : request.description(), founder);
        return ResponseEntity.created(URI.create("/groups/" + group.id())).body(GroupResponse.of(group));
    }

    @GetMapping("/groups/{groupId}")
    GroupResponse view(@PathVariable long groupId) {
        return GroupResponse.of(groups.view(groupId));
    }
}
