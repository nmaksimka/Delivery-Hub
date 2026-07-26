ALTER TABLE menu_items
    ALTER COLUMN description TYPE VARCHAR(2000) USING left(description, 2000);
