CREATE TABLE order_recipients (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
