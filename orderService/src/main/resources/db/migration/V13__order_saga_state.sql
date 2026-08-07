ALTER TABLE orders
    ADD COLUMN cancellation_reason VARCHAR(32),
    ADD COLUMN refunded_at         TIMESTAMPTZ;

UPDATE orders SET cancellation_reason = 'UNKNOWN' WHERE status = 'CANCELLED';

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_cancellation_reason CHECK ((status = 'CANCELLED') = (cancellation_reason IS NOT NULL));

CREATE INDEX idx_orders_awaiting_saga ON orders (status, updated_at)
    WHERE status IN ('CREATED', 'PAYMENT_HELD', 'COURIER_ASSIGNED');
