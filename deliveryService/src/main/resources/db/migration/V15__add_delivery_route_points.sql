ALTER TABLE deliveries
    ADD COLUMN pickup_latitude   DOUBLE PRECISION,
    ADD COLUMN pickup_longitude  DOUBLE PRECISION,
    ADD COLUMN dropoff_latitude  DOUBLE PRECISION,
    ADD COLUMN dropoff_longitude DOUBLE PRECISION,
    ADD COLUMN order_confirmed_at TIMESTAMPTZ;
