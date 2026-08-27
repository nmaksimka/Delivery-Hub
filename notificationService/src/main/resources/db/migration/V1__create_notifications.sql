CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    order_id        BIGINT        NOT NULL,
    type            VARCHAR(32)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    amount          NUMERIC(12, 2),
    reason          VARCHAR(200),
    created_at      TIMESTAMPTZ   NOT NULL,
    read_at         TIMESTAMPTZ,
    mail_status     VARCHAR(16)   NOT NULL,
    mail_attempts   INT           NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    sent_at         TIMESTAMPTZ,
    last_error      VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_pending_mail ON notifications (next_attempt_at) WHERE mail_status = 'PENDING';
