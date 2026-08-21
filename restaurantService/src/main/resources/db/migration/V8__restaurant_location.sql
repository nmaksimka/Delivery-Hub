ALTER TABLE restaurants
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD CONSTRAINT ck_restaurants_location CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT ck_restaurants_latitude CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_restaurants_longitude CHECK (longitude BETWEEN -180 AND 180);

UPDATE restaurants
SET latitude  = 56.455 + mod(abs(hashtext(coalesce(external_id, id::text))), 5000) / 100000.0,
    longitude = 84.940 + mod(abs(hashtext(coalesce(external_id, id::text) || ':lon')), 8000) / 100000.0
WHERE city = 'Томск' AND latitude IS NULL;
