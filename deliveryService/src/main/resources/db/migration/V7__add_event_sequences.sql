ALTER TABLE deliveries
    ADD COLUMN event_sequence      BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_order_sequence BIGINT NOT NULL DEFAULT 0;
