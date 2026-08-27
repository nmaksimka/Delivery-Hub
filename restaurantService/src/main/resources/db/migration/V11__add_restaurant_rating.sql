ALTER TABLE restaurants
    ADD COLUMN rating_sum   BIGINT  NOT NULL DEFAULT 0,
    ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_restaurants_rating CHECK (rating_sum >= 0 AND rating_count >= 0 AND rating_sum <= rating_count * 5);
