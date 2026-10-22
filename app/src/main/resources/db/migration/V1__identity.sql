CREATE TABLE members (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    password_hash TEXT NOT NULL
);

-- Demo members. Every one signs in with the password "rsvp-demo" (stored as a bcrypt hash).
INSERT INTO members (username, name, password_hash) VALUES
    ('alice', 'Alice', '{bcrypt}$2a$10$rsP4lRk5YmYHUwihqKYgsO567DXAxqwebF/C9vycjkPYU.uXRf6u2'),
    ('bob', 'Bob', '{bcrypt}$2a$10$rsP4lRk5YmYHUwihqKYgsO567DXAxqwebF/C9vycjkPYU.uXRf6u2'),
    ('carol', 'Carol', '{bcrypt}$2a$10$rsP4lRk5YmYHUwihqKYgsO567DXAxqwebF/C9vycjkPYU.uXRf6u2'),
    ('dave', 'Dave', '{bcrypt}$2a$10$rsP4lRk5YmYHUwihqKYgsO567DXAxqwebF/C9vycjkPYU.uXRf6u2'),
    ('erin', 'Erin', '{bcrypt}$2a$10$rsP4lRk5YmYHUwihqKYgsO567DXAxqwebF/C9vycjkPYU.uXRf6u2'),
    ('frank', 'Frank', '{bcrypt}$2a$10$rsP4lRk5YmYHUwihqKYgsO567DXAxqwebF/C9vycjkPYU.uXRf6u2');
