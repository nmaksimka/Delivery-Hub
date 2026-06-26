DELETE FROM outbox_events;

ALTER TABLE outbox_events
    ALTER COLUMN payload TYPE BYTEA USING NULL;
