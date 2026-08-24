CREATE TABLE reviews (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    restaurant_id     BIGINT       NOT NULL,
    courier_id        BIGINT,
    restaurant_rating SMALLINT     NOT NULL,
    courier_rating    SMALLINT,
    comment           VARCHAR(2000) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uq_reviews_order UNIQUE (order_id),
    CONSTRAINT ck_reviews_restaurant_rating CHECK (restaurant_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_courier_rating CHECK (courier_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_courier_pair CHECK (courier_rating IS NULL OR courier_id IS NOT NULL)
);

CREATE INDEX idx_reviews_restaurant ON reviews (restaurant_id, created_at DESC);
