ALTER TABLE payments DROP CONSTRAINT ck_payments_amount;
ALTER TABLE payments ADD CONSTRAINT ck_payments_amount CHECK (amount >= 0);
