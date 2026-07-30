ALTER TABLE outbox_events
    ADD COLUMN attempts        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_error      VARCHAR(1000),
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN parked_at       TIMESTAMPTZ;

DROP INDEX idx_outbox_events_pending;

CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at)
    WHERE published_at IS NULL AND parked_at IS NULL;

CREATE INDEX idx_outbox_events_published ON outbox_events (published_at)
    WHERE published_at IS NOT NULL;
