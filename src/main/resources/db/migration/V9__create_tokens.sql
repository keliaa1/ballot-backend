-- V9: Create tokens table (refresh tokens and blacklisted access tokens)
CREATE TABLE tokens (
    id         VARCHAR(36)  PRIMARY KEY,
    token      TEXT         NOT NULL UNIQUE,
    type       VARCHAR(20)  NOT NULL,
    user_id    VARCHAR(36)  REFERENCES users(id),
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_tokens_token ON tokens(token);
CREATE INDEX idx_tokens_user_id ON tokens(user_id);
CREATE INDEX idx_tokens_type ON tokens(type);
CREATE INDEX idx_tokens_expires_at ON tokens(expires_at);
