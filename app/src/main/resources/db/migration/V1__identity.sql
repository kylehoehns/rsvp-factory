CREATE TABLE member (
    id            BIGINT PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);
