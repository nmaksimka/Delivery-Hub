CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT         NOT NULL,
    user_id            BIGINT         NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    status             VARCHAR(16)    NOT NULL,
    provider           VARCHAR(32)    NOT NULL,
    provider_reference VARCHAR(64),
    decline_reason     VARCHAR(64),
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('HELD', 'CAPTURED', 'RELEASED', 'REFUNDED', 'REJECTED')),
    CONSTRAINT ck_payments_rejected CHECK ((status = 'REJECTED') = (decline_reason IS NOT NULL))
);

CREATE INDEX idx_payments_user ON payments (user_id);
