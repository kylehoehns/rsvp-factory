CREATE TABLE groups (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL
);

-- member_id refers to a member of the identity module by id only: no foreign key across modules.
CREATE TABLE group_membership (
    group_id  BIGINT  NOT NULL REFERENCES groups (id),
    member_id BIGINT  NOT NULL,
    organizer BOOLEAN NOT NULL,
    PRIMARY KEY (group_id, member_id)
);
