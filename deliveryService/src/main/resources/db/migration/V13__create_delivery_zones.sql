CREATE TABLE delivery_zones (
    restaurant_id  BIGINT PRIMARY KEY,
    center         geography(Point, 4326)   NOT NULL,
    area           geography(Polygon, 4326) NOT NULL,
    max_distance_m INTEGER                  NOT NULL CHECK (max_distance_m > 0),
    updated_at     TIMESTAMPTZ              NOT NULL
);

CREATE INDEX idx_delivery_zones_area ON delivery_zones USING GIST (area);
