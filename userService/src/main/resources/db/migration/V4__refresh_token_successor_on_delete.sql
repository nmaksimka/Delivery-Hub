ALTER TABLE refresh_tokens
    DROP CONSTRAINT refresh_tokens_replaced_by_fkey;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id) ON DELETE SET NULL;
