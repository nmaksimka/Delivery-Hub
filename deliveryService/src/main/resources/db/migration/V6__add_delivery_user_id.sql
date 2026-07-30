ALTER TABLE deliveries
    ADD COLUMN user_id BIGINT;

CREATE INDEX idx_deliveries_user_id ON deliveries (user_id);
