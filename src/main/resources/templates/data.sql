-- Optional seed data for manual local testing. Do not run on production because it truncates product tables.
TRUNCATE TABLE product_colors, product_sizes, products RESTART IDENTITY CASCADE;

INSERT INTO products (id, name, category, room, part_type, rating, is_popular, is_discount, status, description, deleted, created_at)
VALUES
(1, 'Venesiya Eksklüziv İtalyan Tülü', 'Tüllər', 'Qonaq otağı', 'Tül', 5.0, true, true, 'Endirimli', 'Klassik İtalyan zərifliyi ilə toxunmuş premium tül.', false, NOW()),
(2, 'Royal Navy Velur Fonluq', 'Fonluqlar', 'Yataq otağı', 'Velur', 4.8, true, true, 'Endirimli', 'Yüksək keyfiyyətli velur materialdan hazırlanmış blackout fonluq.', false, NOW()),
(3, 'Vogue Wood Zebra', 'Jalüzlər', 'Ofis', 'Zebra', 4.4, true, false, 'Popular', 'Modern ofis və mətbəxlər üçün taxta teksturalı zebra pərdə sistemi.', false, NOW()),
(4, 'İnci Naxışlı Aksesuar', 'Aksesuarlar', 'Dəhliz', 'Aksesuar', 4.2, false, false, 'Standart', 'Pərdələr üçün zərif inci naxışlı tutacaq.', false, NOW());

INSERT INTO product_colors (product_id, name, color_hex, image_url)
VALUES
(1, 'Krem', '#FFFDD0', 'https://images.unsplash.com/photo-1620807534720-3775b9b1e93f'),
(1, 'Ağ', '#FFFFFF', 'https://images.unsplash.com/photo-1513694203232-719a280e022f'),
(2, 'Tünd Göy', '#000080', 'https://images.unsplash.com/photo-1594191370213-91b7d5598696'),
(3, 'Qəhvəyi', '#4B3621', 'https://images.unsplash.com/photo-1497366216548-37526070297c');

INSERT INTO product_sizes (product_id, size_value, price, old_price)
VALUES
(1, '200x300', 120, 150),
(2, '220x320', 180, 220),
(3, 'Standart', 90, NULL),
(4, 'Standart', 25, NULL);
