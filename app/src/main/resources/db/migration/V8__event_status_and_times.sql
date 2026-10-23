-- Statuses are the names of EventStatus; the application stores them by name.
ALTER TABLE event
    ADD CONSTRAINT event_status_known CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    ADD CONSTRAINT event_ends_after_start CHECK (ends_at > starts_at);
