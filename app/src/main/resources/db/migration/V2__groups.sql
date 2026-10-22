CREATE TABLE groups (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT NOT NULL
);

-- member_id refers to identity's members by id only: no foreign key across module boundaries.
CREATE TABLE group_members (
    group_id  BIGINT  NOT NULL REFERENCES groups (id),
    member_id BIGINT  NOT NULL,
    organizer BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (group_id, member_id)
);
