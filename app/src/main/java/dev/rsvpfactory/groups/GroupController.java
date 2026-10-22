package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.CurrentMember;
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

    private final Groups groups;
    private final CurrentMember currentMember;

    GroupController(Groups groups, CurrentMember currentMember) {
        this.groups = groups;
        this.currentMember = currentMember;
    }

    @PostMapping("/groups")
    ResponseEntity<GroupView> start(@Valid @RequestBody StartGroup request) {
        GroupView group = groups.start(currentMember.require(), request);
        return ResponseEntity.created(URI.create("/groups/" + group.id())).body(group);
    }

    @GetMapping("/groups/{groupId}")
    GroupView view(@PathVariable long groupId) {
        return groups.view(groupId);
    }
}
