ALTER TABLE menu_items
    ADD COLUMN section     VARCHAR(16) NOT NULL DEFAULT 'DISH',
    ADD COLUMN energy_kcal NUMERIC(7, 1),
    ADD COLUMN protein_g   NUMERIC(7, 1),
    ADD COLUMN fat_g       NUMERIC(7, 1),
    ADD COLUMN carbs_g     NUMERIC(7, 1),
    ADD COLUMN nutri_score VARCHAR(1);

ALTER TABLE menu_items
    ADD CONSTRAINT ck_menu_items_section CHECK (section IN ('DISH', 'PRODUCT', 'DRINK')),
    ADD CONSTRAINT ck_menu_items_nutri_score CHECK (nutri_score IS NULL OR nutri_score IN ('A', 'B', 'C', 'D', 'E'));

CREATE INDEX idx_menu_items_restaurant_section ON menu_items (restaurant_id, section);
