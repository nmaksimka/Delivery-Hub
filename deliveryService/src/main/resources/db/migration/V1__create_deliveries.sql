CREATE TABLE deliveries (
    id                      BIGSERIAL PRIMARY KEY,
    order_id                BIGINT      NOT NULL UNIQUE,
    courier_id              BIGINT,
    status                  VARCHAR(32) NOT NULL,
    estimated_delivery_time TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
