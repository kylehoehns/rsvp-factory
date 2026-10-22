package dev.rsvpfactory.groups;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class GroupRepository {

    record GroupRow(long id, String name, String description) {}

    private final JdbcClient jdbc;

    GroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insert(String name, String description) {
        return jdbc.sql("INSERT INTO groups (name, description) VALUES (:name, :description) RETURNING id")
                .param("name", name)
                .param("description", description)
                .query(Long.class)
                .single();
    }

    void addMember(long groupId, long memberId, boolean organizer) {
        jdbc.sql("INSERT INTO group_membership (group_id, member_id, organizer)"
                        + " VALUES (:groupId, :memberId, :organizer)")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .param("organizer", organizer)
                .update();
    }

    Optional<GroupRow> find(long id) {
        return jdbc.sql("SELECT id, name, description FROM groups WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> new GroupRow(rs.getLong("id"), rs.getString("name"), rs.getString("description")))
                .optional();
    }

    List<Long> organizerIds(long groupId) {
        return jdbc.sql("SELECT member_id FROM group_membership WHERE group_id = :groupId AND organizer")
                .param("groupId", groupId)
                .query((rs, row) -> rs.getLong("member_id"))
                .list();
    }
}
