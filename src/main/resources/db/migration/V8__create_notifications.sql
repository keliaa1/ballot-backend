-- V8: Create notifications table
CREATE TABLE notifications (
    id         VARCHAR(36)   PRIMARY KEY,
    user_id    VARCHAR(36)   NOT NULL REFERENCES users(id),
    title      VARCHAR(255),
    message    VARCHAR(1000) NOT NULL,
    type       VARCHAR(20)   NOT NULL DEFAULT 'IN_APP',
    status     VARCHAR(20)   NOT NULL DEFAULT 'UNREAD',
    action_url VARCHAR(500),
    created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    read_at    TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
