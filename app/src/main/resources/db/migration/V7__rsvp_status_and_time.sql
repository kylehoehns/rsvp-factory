-- Existing rows take the migration's time; their id order still breaks the tie, so the waitlist order is unchanged.
-- No default stays on the column: the application sets created_at from its Clock.
ALTER TABLE rsvp
    ADD CONSTRAINT rsvp_status_known CHECK (status IN ('confirmed', 'waitlisted')),
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();
ALTER TABLE rsvp ALTER COLUMN created_at DROP DEFAULT;
