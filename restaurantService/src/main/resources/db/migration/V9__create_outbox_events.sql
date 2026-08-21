CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    topic        VARCHAR(128) NOT NULL,
    payload      BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at) WHERE published_at IS NULL;
