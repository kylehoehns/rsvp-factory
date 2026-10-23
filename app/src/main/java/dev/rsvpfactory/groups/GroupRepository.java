package dev.rsvpfactory.groups;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class GroupRepository {

    record GroupRow(UUID id, String name, String description) {}

    private final JdbcClient jdbc;

    GroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID insert(String name, String description) {
        return jdbc.sql("INSERT INTO groups (name, description) VALUES (:name, :description) RETURNING id")
                .param("name", name)
                .param("description", description)
                .query(UUID.class)
                .single();
    }

    void addMember(UUID groupId, UUID memberId, boolean organizer) {
        jdbc.sql("INSERT INTO group_membership (group_id, member_id, organizer)"
                        + " VALUES (:groupId, :memberId, :organizer)")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .param("organizer", organizer)
                .update();
    }

    Optional<GroupRow> find(UUID id) {
        return jdbc.sql("SELECT id, name, description FROM groups WHERE id = :id")
                .param("id", id)
                .query((rs, row) ->
                        new GroupRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description")))
                .optional();
    }

    List<UUID> organizerIds(UUID groupId) {
        return jdbc.sql("SELECT member_id FROM group_membership WHERE group_id = :groupId AND organizer")
                .param("groupId", groupId)
                .query((rs, row) -> rs.getObject("member_id", UUID.class))
                .list();
    }

    int memberCount(UUID groupId) {
        return jdbc.sql("SELECT count(*) FROM group_membership WHERE group_id = :groupId")
                .param("groupId", groupId)
                .query(Integer.class)
                .single();
    }

    /** Adds the member as a non-organizer; does nothing if they already belong. */
    void join(UUID groupId, UUID memberId) {
        jdbc.sql("INSERT INTO group_membership (group_id, member_id, organizer)"
                        + " VALUES (:groupId, :memberId, FALSE) ON CONFLICT DO NOTHING")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .update();
    }

    /** Removes the member; true if they belonged. */
    boolean leave(UUID groupId, UUID memberId) {
        return jdbc.sql("DELETE FROM group_membership WHERE group_id = :groupId AND member_id = :memberId")
                        .param("groupId", groupId)
                        .param("memberId", memberId)
                        .update()
                > 0;
    }

    boolean isMember(UUID groupId, UUID memberId) {
        return exists(groupId, memberId, "");
    }

    boolean isOrganizer(UUID groupId, UUID memberId) {
        return exists(groupId, memberId, " AND organizer");
    }

    void makeOrganizer(UUID groupId, UUID memberId) {
        jdbc.sql("UPDATE group_membership SET organizer = TRUE WHERE group_id = :groupId AND member_id = :memberId")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .update();
    }

    private boolean exists(UUID groupId, UUID memberId, String extraCondition) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM group_membership"
                        + " WHERE group_id = :groupId AND member_id = :memberId" + extraCondition + ")")
                .param("groupId", groupId)
                .param("memberId", memberId)
                .query(Boolean.class)
                .single();
    }
}
