-- event_id and member_id refer to an event and a member of other modules by id only: no foreign keys across modules.
-- Arrival order is the id order: the waitlist is the waitlisted rows by id.
CREATE TABLE rsvp (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    party_size INTEGER     NOT NULL CHECK (party_size > 0),
    status     VARCHAR(20) NOT NULL,
    UNIQUE (event_id, member_id)
);
