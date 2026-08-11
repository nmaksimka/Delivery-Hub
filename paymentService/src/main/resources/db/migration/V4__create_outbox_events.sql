CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(128)  NOT NULL,
    payload         BYTEA         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    next_attempt_at TIMESTAMPTZ,
    parked_at       TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at)
    WHERE published_at IS NULL AND parked_at IS NULL;

CREATE INDEX idx_outbox_events_published ON outbox_events (published_at)
    WHERE published_at IS NOT NULL;
