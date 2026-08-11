CREATE TABLE promo_redemptions (
    id          BIGSERIAL PRIMARY KEY,
    promo_id    BIGINT         NOT NULL,
    user_id     BIGINT         NOT NULL,
    order_id    BIGINT         NOT NULL,
    discount    NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_promo_redemptions_promo FOREIGN KEY (promo_id) REFERENCES promo_codes (id),
    CONSTRAINT uq_promo_redemptions_promo_order UNIQUE (promo_id, order_id)
);

CREATE INDEX idx_promo_redemptions_promo_user ON promo_redemptions (promo_id, user_id);
CREATE INDEX idx_promo_redemptions_order ON promo_redemptions (order_id);
