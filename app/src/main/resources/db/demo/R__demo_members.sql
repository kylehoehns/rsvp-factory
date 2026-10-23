-- Demo members for local development and tests. This location is only added by the dev profile and the test
-- configurations (see application-dev.properties), so a production database never gets accounts with a known password.
-- Their ids are fixed so tests can name them. Every demo member's password is "rsvp-demo".
INSERT INTO member (id, username, password_hash) VALUES
    ('0197a000-0000-7000-8000-000000000001', 'alice', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    ('0197a000-0000-7000-8000-000000000002', 'bob', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    ('0197a000-0000-7000-8000-000000000003', 'carol', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    ('0197a000-0000-7000-8000-000000000004', 'dave', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe')
ON CONFLICT (username) DO NOTHING;
