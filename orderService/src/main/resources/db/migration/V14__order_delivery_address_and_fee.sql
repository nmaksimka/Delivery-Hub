ALTER TABLE orders
    ADD COLUMN delivery_fee        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN eta_minutes         INTEGER,
    ADD COLUMN delivery_address_id BIGINT,
    ADD COLUMN delivery_address    VARCHAR(400),
    ADD COLUMN delivery_comment    VARCHAR(300),
    ADD COLUMN delivery_latitude   NUMERIC(9, 6),
    ADD COLUMN delivery_longitude  NUMERIC(9, 6);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_delivery_fee CHECK (delivery_fee >= 0),
    ADD CONSTRAINT ck_orders_delivery_point CHECK ((delivery_latitude IS NULL) = (delivery_longitude IS NULL));
