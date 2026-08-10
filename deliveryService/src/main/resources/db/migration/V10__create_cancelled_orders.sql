CREATE TABLE cancelled_orders (
    order_id     BIGINT PRIMARY KEY,
    cancelled_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cancelled_orders_cancelled_at ON cancelled_orders (cancelled_at);

INSERT INTO cancelled_orders (order_id, cancelled_at)
SELECT order_id, updated_at FROM deliveries
WHERE status = 'CANCELLED' AND courier_id IS NULL AND created_at = updated_at;

DELETE FROM deliveries
WHERE status = 'CANCELLED' AND courier_id IS NULL AND created_at = updated_at;
