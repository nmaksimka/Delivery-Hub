CREATE TABLE favorite_restaurants (
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    restaurant_id BIGINT      NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, restaurant_id)
);
