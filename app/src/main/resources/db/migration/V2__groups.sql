-- Members are referred to by id only: no foreign key into the identity module's tables.
CREATE TABLE community_group (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        text NOT NULL,
    description text NOT NULL
);

CREATE TABLE group_membership (
    group_id  bigint  NOT NULL REFERENCES community_group (id),
    member_id bigint  NOT NULL,
    organizer boolean NOT NULL,
    PRIMARY KEY (group_id, member_id)
);
