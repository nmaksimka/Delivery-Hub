ALTER TABLE orders
    ADD COLUMN delivery_distance_m        INTEGER,
    ADD COLUMN delivery_weather_surcharge NUMERIC(12, 2),
    ADD COLUMN delivery_route_source      VARCHAR(20);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_delivery_breakdown CHECK (
        (delivery_distance_m IS NULL OR delivery_distance_m >= 0)
        AND (delivery_weather_surcharge IS NULL OR delivery_weather_surcharge >= 0));
