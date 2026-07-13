ALTER TABLE restaurants
    ADD COLUMN cuisine         VARCHAR(64),
    ADD COLUMN city            VARCHAR(64),
    ADD COLUMN image_url       VARCHAR(512),
    ADD COLUMN external_source VARCHAR(32),
    ADD COLUMN external_id     VARCHAR(64);

CREATE UNIQUE INDEX uq_restaurants_external ON restaurants (external_source, external_id)
    WHERE external_source IS NOT NULL;

ALTER TABLE menu_items
    ADD COLUMN image_url       VARCHAR(512),
    ADD COLUMN external_source VARCHAR(32),
    ADD COLUMN external_id     VARCHAR(64);

CREATE UNIQUE INDEX uq_menu_items_external ON menu_items (restaurant_id, external_source, external_id)
    WHERE external_source IS NOT NULL;

CREATE TABLE import_jobs (
    id                  UUID PRIMARY KEY,
    source              VARCHAR(32) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    total               INTEGER     NOT NULL DEFAULT 0,
    processed           INTEGER     NOT NULL DEFAULT 0,
    restaurants_created INTEGER     NOT NULL DEFAULT 0,
    items_created       INTEGER     NOT NULL DEFAULT 0,
    items_updated       INTEGER     NOT NULL DEFAULT 0,
    error               VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_import_jobs_active ON import_jobs (source)
    WHERE status IN ('PENDING', 'RUNNING');
