ALTER TABLE restaurants
    ADD COLUMN owner_id BIGINT;

CREATE INDEX idx_restaurants_owner_id ON restaurants (owner_id) WHERE owner_id IS NOT NULL;
