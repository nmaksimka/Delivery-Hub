ALTER TABLE orders
    ADD COLUMN event_sequence         BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_delivery_sequence BIGINT NOT NULL DEFAULT 0;
