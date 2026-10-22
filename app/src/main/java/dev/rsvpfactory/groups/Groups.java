package dev.rsvpfactory.groups;

import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import dev.rsvpfactory.problems.Refusal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starting and viewing groups. */
@Service
public class Groups {

    private final JdbcClient jdbc;
    private final Members members;

    Groups(JdbcClient jdbc, Members members) {
        this.jdbc = jdbc;
        this.members = members;
    }

    /** Starts a group; its creator joins it as the first organizer. */
    @Transactional
    public GroupView start(Member creator, StartGroup request) {
        String description = request.description() == null ? "" : request.description();
        long id = jdbc.sql("INSERT INTO community_group (name, description) VALUES (:name, :description) RETURNING id")
                .param("name", request.name().strip())
                .param("description", description)
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO group_membership (group_id, member_id, organizer) VALUES (:group, :member, true)")
                .param("group", id)
                .param("member", creator.id())
                .update();
        return view(id);
    }

    @Transactional(readOnly = true)
    public GroupView view(long id) {
        GroupView group = jdbc.sql("SELECT name, description FROM community_group WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> new GroupView(id, rs.getString("name"), rs.getString("description"), List.of()))
                .optional()
                .orElseThrow(() -> new Refusal(HttpStatus.NOT_FOUND, "group-not-found", "There is no such group."));
        List<Long> organizerIds = jdbc.sql("SELECT member_id FROM group_membership WHERE group_id = :id AND organizer")
                .param("id", id)
                .query((rs, row) -> rs.getLong("member_id"))
                .list();
        return new GroupView(group.id(), group.name(), group.description(), members.findAll(organizerIds));
    }
}
