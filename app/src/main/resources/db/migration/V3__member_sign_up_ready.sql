-- Room for stronger password encoders (Argon2, scrypt) than bcrypt without another migration.
ALTER TABLE member ALTER COLUMN password_hash TYPE VARCHAR(255);

-- Members get their ids from the database, so sign-up never collides with existing rows. Continue after any ids
-- already handed out by hand (the demo members of a development database).
ALTER TABLE member ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;
SELECT setval(pg_get_serial_sequence('member', 'id'), (SELECT COALESCE(MAX(id), 0) + 1 FROM member), false);
