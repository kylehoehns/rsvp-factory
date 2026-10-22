package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.CurrentMember;
import dev.rsvpfactory.problems.RuleBrokenException;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private static long parse(String groupId) {
        try {
            return Long.parseLong(groupId);
        } catch (NumberFormatException e) {
            throw RuleBrokenException.notFound("group", groupId);
        }
    }
}
