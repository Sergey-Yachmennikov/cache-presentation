CREATE TABLE notifications (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    message    VARCHAR(500) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL
);
