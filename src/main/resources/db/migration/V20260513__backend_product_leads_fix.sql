-- Manual PostgreSQL migration for product/lead CRM fixes.
-- Safe to run multiple times; it does not drop existing data.

ALTER TABLE IF EXISTS products
    ADD COLUMN IF NOT EXISTS part_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS rating DOUBLE PRECISION;

UPDATE products SET part_type = 'Standart' WHERE part_type IS NULL OR trim(part_type) = '';
UPDATE products SET rating = 5 WHERE rating IS NULL;

ALTER TABLE IF EXISTS product_sizes
    ADD COLUMN IF NOT EXISTS size_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS price DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS old_price DOUBLE PRECISION;

UPDATE product_sizes SET size_value = 'Standart' WHERE size_value IS NULL OR trim(size_value) = '';
UPDATE product_sizes SET price = 0 WHERE price IS NULL;

ALTER TABLE IF EXISTS product_colors
    ADD COLUMN IF NOT EXISTS color_hex VARCHAR(50),
    ADD COLUMN IF NOT EXISTS image_url TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_colors' AND column_name = 'code'
    ) THEN
        UPDATE product_colors SET color_hex = code WHERE color_hex IS NULL AND code IS NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_colors' AND column_name = 'main_image'
    ) THEN
        UPDATE product_colors SET image_url = main_image WHERE image_url IS NULL AND main_image IS NOT NULL;
    END IF;
END $$;

ALTER TABLE IF EXISTS product_colors
    ALTER COLUMN image_url TYPE TEXT;

UPDATE product_colors SET name = 'Standart' WHERE name IS NULL OR trim(name) = '';
UPDATE product_colors SET color_hex = '#cccccc' WHERE color_hex IS NULL OR trim(color_hex) = '';
UPDATE product_colors SET image_url = '' WHERE image_url IS NULL;

ALTER TABLE IF EXISTS leads
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS message TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS contacted BOOLEAN,
    ADD COLUMN IF NOT EXISTS promo_code VARCHAR(255),
    ADD COLUMN IF NOT EXISTS requested_products TEXT,
    ADD COLUMN IF NOT EXISTS liked_products_summary TEXT,
    ADD COLUMN IF NOT EXISTS liked_product_links TEXT,
    ADD COLUMN IF NOT EXISTS total_amount DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS referrer VARCHAR(255),
    ADD COLUMN IF NOT EXISTS visualization_image_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT false;

UPDATE leads SET deleted = false WHERE deleted IS NULL;
UPDATE leads SET status = 'NEW' WHERE status IS NULL OR trim(status) = '' OR upper(status) IN ('YENİ', 'YENI');
UPDATE leads SET status = 'CONTACTED' WHERE upper(status) IN ('ZƏNG EDİLDİ', 'ZENG EDILDI', 'CALLED');
UPDATE leads SET contacted = false WHERE contacted IS NULL;
UPDATE leads SET referrer = 'WEBSITE' WHERE referrer IS NULL OR trim(referrer) = '' OR upper(referrer) = 'WEB';
UPDATE leads SET source = 'DISCOUNT' WHERE upper(source) IN ('PROMO', 'PROMO_CODE', 'PROMOCODE');
UPDATE leads SET source = 'VISUAL' WHERE upper(source) IN ('VISUALIZATION', 'VISUALISATION', 'VIRTUAL', 'VIRTUAL_DESIGN', 'VIRTUAL_DIZAYN', 'VIRTUAL_DİZAYN', 'DIZAYN', 'DİZAYN', 'DESIGN');
UPDATE leads SET source = 'MEASURE' WHERE upper(source) = 'MEASUREMENT';
UPDATE leads SET source = 'HEART' WHERE upper(source) IN ('WISHLIST', 'FAVORITES', 'FAVORITE', 'LIKED', 'LIKES');
UPDATE leads SET source = 'ALL' WHERE source IS NULL OR trim(source) = '' OR upper(source) IN ('GENERAL', 'CONTACT', 'UNKNOWN');
UPDATE leads SET created_at = NOW() WHERE created_at IS NULL;
UPDATE leads SET updated_at = NOW() WHERE updated_at IS NULL;

-- Lead admin list compatibility fix: older databases may have name/customer_name instead of full_name.
ALTER TABLE IF EXISTS leads
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'leads' AND column_name = 'name'
    ) THEN
        UPDATE leads SET full_name = name WHERE (full_name IS NULL OR trim(full_name) = '') AND name IS NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'leads' AND column_name = 'customer_name'
    ) THEN
        UPDATE leads SET full_name = customer_name WHERE (full_name IS NULL OR trim(full_name) = '') AND customer_name IS NOT NULL;
    END IF;
END $$;
