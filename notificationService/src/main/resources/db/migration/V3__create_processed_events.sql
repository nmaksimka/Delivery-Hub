CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
