CREATE TABLE promo_codes (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(64)    NOT NULL,
    type              VARCHAR(16)    NOT NULL,
    discount_value    NUMERIC(10, 2) NOT NULL,
    min_order_amount  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    valid_from        TIMESTAMPTZ,
    valid_to          TIMESTAMPTZ,
    max_redemptions   INTEGER,
    max_per_user      INTEGER,
    restaurant_id     BIGINT,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    used_count        INTEGER        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_promo_codes_code UNIQUE (code),
    CONSTRAINT ck_promo_codes_used_count CHECK (used_count >= 0)
);

CREATE UNIQUE INDEX uq_promo_codes_code_upper ON promo_codes (UPPER(code));
