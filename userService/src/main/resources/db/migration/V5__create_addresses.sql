CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label           VARCHAR(40)   NOT NULL,
    city            VARCHAR(80)   NOT NULL,
    street          VARCHAR(160)  NOT NULL,
    house           VARCHAR(20)   NOT NULL,
    apartment       VARCHAR(20),
    entrance        VARCHAR(20),
    floor           VARCHAR(20),
    courier_comment VARCHAR(300),
    latitude        NUMERIC(9, 6),
    longitude       NUMERIC(9, 6),
    is_default      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_addresses_user ON addresses (user_id, created_at, id);

CREATE UNIQUE INDEX uq_addresses_one_default_per_user ON addresses (user_id) WHERE is_default;
