-- Demo members for local use and the acceptance suite. Every password is "rsvp-demo", stored as a bcrypt hash.
CREATE TABLE member (
    id            bigint PRIMARY KEY,
    username      text NOT NULL UNIQUE,
    password_hash text NOT NULL
);

INSERT INTO member (id, username, password_hash) VALUES
    (1, 'alice', '{bcrypt}$2b$12$pkcu4kv3zzb2qBfwzuKk8OKicpEc6vTpj4bsUeVy/H1OrnMn/1yxW'),
    (2, 'bob',   '{bcrypt}$2b$12$RQP0O4w4Dw2cZnE1Wqlej.CI1qMmkBKM73lF15OMoja2w9FN.07jC'),
    (3, 'carol', '{bcrypt}$2b$12$5T8bU8UKCc2n3YdJUl2Jv.R0bRDzmBbXbPYqxkQOhdIzi9eRaexEu'),
    (4, 'dave',  '{bcrypt}$2b$12$ZAg2tGnE3LbKA43br9N.JuhsKLFyWGkI4mNx2u1y5XAMWmZOF3zKW'),
    (5, 'erin',  '{bcrypt}$2b$12$bGrDLbBcDb8n3LH2vIUqwe2haJHsNpUj9VpaUxjstYnDnbS3GW8N6');
