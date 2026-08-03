CREATE TABLE idempotency_keys (
    user_id         BIGINT       NOT NULL,
    method          VARCHAR(10)  NOT NULL,
    path            VARCHAR(255) NOT NULL,
    key             VARCHAR(64)  NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    response_status SMALLINT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, method, path, key),
    CONSTRAINT ck_idempotency_keys_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_keys_response CHECK (status = 'IN_PROGRESS' OR response_status IS NOT NULL)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
