package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.problems.RuleBrokenException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GroupController {

    private final GroupService groups;
    private final CurrentMember currentMember;

    GroupController(GroupService groups, CurrentMember currentMember) {
        this.groups = groups;
        this.currentMember = currentMember;
    }

    @PostMapping("/groups")
    ResponseEntity<GroupView> start(@Valid @RequestBody StartGroupRequest request) {
        String description = request.description() == null ? "" : request.description();
        GroupView group = groups.start(currentMember.get(), request.name(), description);
        return ResponseEntity.created(URI.create("/groups/" + group.id())).body(group);
    }

    @GetMapping("/groups/{groupId}")
    GroupView view(@PathVariable String groupId) {
        return groups.view(parse(groupId));
    }

    @PutMapping("/groups/{groupId}/members/me")
    GroupView join(@PathVariable String groupId) {
        return groups.join(currentMember.get(), parse(groupId));
    }

    @DeleteMapping("/groups/{groupId}/members/me")
    ResponseEntity<Void> leave(@PathVariable String groupId) {
        groups.leave(currentMember.get(), parse(groupId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{groupId}/organizers")
    GroupView makeOrganizer(@PathVariable String groupId, @Valid @RequestBody MakeOrganizerRequest request) {
        return groups.makeOrganizer(currentMember.get(), parse(groupId), request.memberId());
    }

    private static UUID parse(String groupId) {
        try {
            return UUID.fromString(groupId);
        } catch (IllegalArgumentException e) {
            throw RuleBrokenException.notFound("group", groupId);
        }
    }
}
