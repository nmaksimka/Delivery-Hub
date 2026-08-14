ALTER TABLE addresses ADD COLUMN geo_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

UPDATE addresses SET geo_status = 'LOCATED' WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

ALTER TABLE addresses ADD CONSTRAINT chk_addresses_geo_status CHECK (geo_status IN ('PENDING', 'LOCATED', 'NOT_FOUND'));

CREATE INDEX idx_addresses_geo_pending ON addresses (updated_at, id) WHERE geo_status = 'PENDING';
