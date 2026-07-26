ALTER TABLE import_jobs
    ADD COLUMN instance_id  VARCHAR(128),
    ADD COLUMN heartbeat_at TIMESTAMPTZ;

UPDATE import_jobs SET heartbeat_at = COALESCE(started_at, created_at);
