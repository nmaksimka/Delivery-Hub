CREATE TABLE restaurants (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(255),
    phone      VARCHAR(32),
    open_time  TIME,
    close_time TIME,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE menu_items (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT         NOT NULL,
    name          VARCHAR(255)   NOT NULL,
    description   TEXT,
    price         NUMERIC(10, 2) NOT NULL,
    category      VARCHAR(100),
    available     BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_menu_items_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE INDEX idx_menu_items_restaurant_id ON menu_items (restaurant_id);
