CREATE TABLE wallets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT         NOT NULL,
    available  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    held       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_available CHECK (available >= 0),
    CONSTRAINT ck_wallets_held CHECK (held >= 0)
);
