package dev.rsvpfactory.groups;

import dev.rsvpfactory.RuleViolation;
import dev.rsvpfactory.identity.Member;
import dev.rsvpfactory.identity.Members;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The groups module's API: start and view groups, and ask who belongs to them. */
@Service
public class Groups {

    private final JdbcClient jdbc;
    private final Members members;

    Groups(JdbcClient jdbc, Members members) {
        this.jdbc = jdbc;
        this.members = members;
    }

    /** Starts a group; {@code founder} becomes its first member and organizer. */
    @Transactional
    public Group start(String name, String description, Member founder) {
        long groupId = jdbc.sql("INSERT INTO groups (name, description) VALUES (:name, :description) RETURNING id")
                .param("name", name)
                .param("description", description)
                .query((rs, rowNum) -> rs.getLong("id"))
                .single();
        jdbc.sql("INSERT INTO group_members (group_id, member_id, organizer) VALUES (:group, :member, TRUE)")
                .param("group", groupId)
                .param("member", founder.id())
                .update();
        return view(groupId);
    }

    /** The group with its organizers, or a {@code group-not-found} refusal. */
    public Group view(long groupId) {
        Group group = jdbc.sql("SELECT id, name, description FROM groups WHERE id = :id")
                .param("id", groupId)
                .query((rs, rowNum) ->
                        new Group(rs.getLong("id"), rs.getString("name"), rs.getString("description"), List.of()))
                .optional()
                .orElseThrow(() -> new RuleViolation(HttpStatus.NOT_FOUND, "group-not-found", "No such group"));
        List<Long> organizerIds = jdbc.sql(
                        "SELECT member_id FROM group_members WHERE group_id = :id AND organizer ORDER BY member_id")
                .param("id", groupId)
                .query((rs, rowNum) -> rs.getLong("member_id"))
                .list();
        return new Group(group.id(), group.name(), group.description(), members.findAll(organizerIds));
    }

    public boolean isMember(long groupId, long memberId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM group_members WHERE group_id = :group AND member_id = :member)")
                .param("group", groupId)
                .param("member", memberId)
                .query(Boolean.class)
                .single();
    }

    public boolean isOrganizer(long groupId, long memberId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM group_members"
                        + " WHERE group_id = :group AND member_id = :member AND organizer)")
                .param("group", groupId)
                .param("member", memberId)
                .query(Boolean.class)
                .single();
    }
}
