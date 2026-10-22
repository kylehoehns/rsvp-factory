-- Demo members for local development and tests. This location is only added by the dev profile and the test
-- configurations (see application-dev.properties), so a production database never gets accounts with a known password.
-- Every demo member's password is "rsvp-demo".
INSERT INTO member (id, username, password_hash) VALUES
    (1, 'alice', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    (2, 'bob', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    (3, 'carol', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe'),
    (4, 'dave', '{bcrypt}$2a$10$suxE3z2Jjfvn3GR0aXBfK.kV75YCOJDtoPWkLjIcj66eQR8dMGoWe')
ON CONFLICT (username) DO NOTHING;
