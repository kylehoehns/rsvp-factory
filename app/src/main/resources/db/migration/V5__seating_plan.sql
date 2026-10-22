-- event_id refers to an event of the events module by id only: no foreign key across modules.
CREATE TABLE seating_plan (
    event_id    BIGINT PRIMARY KEY,
    capacity    INTEGER     NOT NULL CHECK (capacity > 0),
    guest_limit INTEGER     NOT NULL CHECK (guest_limit >= 0),
    rsvp_cutoff TIMESTAMPTZ NOT NULL
);
