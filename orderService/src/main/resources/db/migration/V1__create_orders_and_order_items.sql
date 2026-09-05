CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    restaurant_id BIGINT        NOT NULL,
    status        VARCHAR(32)   NOT NULL,
    total_amount  NUMERIC(10, 2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    menu_item_id   BIGINT         NOT NULL,
    item_name      VARCHAR(255)   NOT NULL,
    quantity       INTEGER        NOT NULL,
    price_per_unit NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_restaurant_id ON orders (restaurant_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
