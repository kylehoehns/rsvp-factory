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

    int memberCount(long groupId) {
        return jdbc.sql("SELECT count(*) FROM group_membership WHERE group_id = :groupId")
                .param("groupId", groupId)
                .query(Integer.class)
                .single();
    }

    /** Adds the member as a non-organizer; does nothing if they already belong. */
    void join(long groupId, long memberId) {
        jdbc.sql("INSERT INTO group_membership (group_id, member_id, organizer)"
                        + " VALUES (:groupId, :memberId, FALSE) ON CONFLICT DO NOTHING")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .update();
    }

    /** Removes the member; true if they belonged. */
    boolean leave(long groupId, long memberId) {
        return jdbc.sql("DELETE FROM group_membership WHERE group_id = :groupId AND member_id = :memberId")
                        .param("groupId", groupId)
                        .param("memberId", memberId)
                        .update()
                > 0;
    }

    boolean isMember(long groupId, long memberId) {
        return exists(groupId, memberId, "");
    }

    boolean isOrganizer(long groupId, long memberId) {
        return exists(groupId, memberId, " AND organizer");
    }

    void makeOrganizer(long groupId, long memberId) {
        jdbc.sql("UPDATE group_membership SET organizer = TRUE WHERE group_id = :groupId AND member_id = :memberId")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .update();
    }

    private boolean exists(long groupId, long memberId, String extraCondition) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM group_membership"
                        + " WHERE group_id = :groupId AND member_id = :memberId" + extraCondition + ")")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .query(Boolean.class)
                .single();
    }
}
