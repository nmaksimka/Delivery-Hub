CREATE UNIQUE INDEX uq_deliveries_active_courier ON deliveries (courier_id)
    WHERE courier_id IS NOT NULL AND status NOT IN ('DELIVERED', 'CANCELLED');
