-- group_id refers to a group of the groups module by id only: no foreign key across modules.
CREATE TABLE event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id    BIGINT        NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(5000) NOT NULL,
    venue       VARCHAR(500)  NOT NULL,
    starts_at   TIMESTAMPTZ   NOT NULL,
    ends_at     TIMESTAMPTZ   NOT NULL,
    status      VARCHAR(20)   NOT NULL
);

CREATE INDEX event_group_id_starts_at ON event (group_id, starts_at);
