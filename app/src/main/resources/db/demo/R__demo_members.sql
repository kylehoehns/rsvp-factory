-- Demo members for local development and tests. This location is only added by the dev profile and the test
-- configurations (see application-dev.properties), so a production database never gets accounts with a known password.
-- Every demo member's password is "rsvp-demo".
INSERT INTO member (username, password_hash) VALUES
    ('alice', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    ('bob', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    ('carol', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    ('dave', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe')
ON CONFLICT (username) DO NOTHING;
