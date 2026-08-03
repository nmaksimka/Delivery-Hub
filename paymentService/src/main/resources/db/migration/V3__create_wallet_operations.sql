CREATE TABLE wallet_operations (
    id              BIGSERIAL PRIMARY KEY,
    wallet_id       BIGINT         NOT NULL REFERENCES wallets (id),
    type            VARCHAR(16)    NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    available_after NUMERIC(12, 2) NOT NULL,
    held_after      NUMERIC(12, 2) NOT NULL,
    order_id        BIGINT,
    idempotency_key VARCHAR(64),
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_wallet_operations_key UNIQUE (wallet_id, idempotency_key),
    CONSTRAINT ck_wallet_operations_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_operations_type CHECK (type IN ('TOP_UP', 'HOLD', 'CAPTURE', 'RELEASE', 'REFUND')),
    CONSTRAINT ck_wallet_operations_source CHECK (
        (type = 'TOP_UP' AND idempotency_key IS NOT NULL AND order_id IS NULL)
        OR (type <> 'TOP_UP' AND order_id IS NOT NULL AND idempotency_key IS NULL))
);

CREATE INDEX idx_wallet_operations_wallet ON wallet_operations (wallet_id, created_at);
CREATE INDEX idx_wallet_operations_order ON wallet_operations (order_id) WHERE order_id IS NOT NULL;
