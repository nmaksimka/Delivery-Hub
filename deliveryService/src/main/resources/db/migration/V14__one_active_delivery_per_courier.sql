UPDATE deliveries
SET courier_id = NULL
WHERE id IN (SELECT id
             FROM (SELECT id,
                          ROW_NUMBER() OVER (PARTITION BY courier_id ORDER BY updated_at DESC, id DESC) AS position
                   FROM deliveries
                   WHERE courier_id IS NOT NULL
                     AND status NOT IN ('DELIVERED', 'CANCELLED')) ranked
             WHERE position > 1);

CREATE UNIQUE INDEX uq_deliveries_active_courier ON deliveries (courier_id)
    WHERE courier_id IS NOT NULL AND status NOT IN ('DELIVERED', 'CANCELLED');
