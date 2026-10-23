-- event_id and member_id refer to an event and a member of other modules by id only: no foreign keys across modules.
-- One check-in per member per event; checking in again replaces the row.
CREATE TABLE check_in (
    event_id      UUID                     NOT NULL,
    member_id     UUID                     NOT NULL,
    arrived       INTEGER                  NOT NULL CHECK (arrived > 0),
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, member_id)
);
