-- ─────────────────────────────────────────────────────────────────────────────
-- DEV seed каталога Tzeva Best: 3 категории (дерево 3 уровня), 2 бренда,
-- 8 товаров с вариантами. Идемпотентно (ON CONFLICT DO NOTHING) — можно запускать
-- многократно. НЕ является Flyway-миграцией: запускается вручную (make seed) только
-- на dev-БД, в test/prod не попадает.
-- ─────────────────────────────────────────────────────────────────────────────
begin;

-- ── Категории (paints → interior → interior-walls) ───────────────────────────
insert into categories (slug, sort_order) values ('paints', 1) on conflict (slug) do nothing;
insert into categories (parent_id, slug, sort_order)
    select id, 'interior', 1 from categories where slug = 'paints'
    on conflict (slug) do nothing;
insert into categories (parent_id, slug, sort_order)
    select id, 'interior-walls', 1 from categories where slug = 'interior'
    on conflict (slug) do nothing;

insert into category_translations (category_id, locale, name)
    select id, l.locale, l.name from categories c
    join (values ('paints','he','צבעים'), ('paints','ru','Краски'), ('paints','en','Paints'),
                 ('interior','he','צבעי פנים'), ('interior','ru','Интерьерные'), ('interior','en','Interior'),
                 ('interior-walls','he','צבעי קיר'), ('interior-walls','ru','Для стен'), ('interior-walls','en','Walls')
         ) as l(slug, locale, name) on l.slug = c.slug
    on conflict (category_id, locale) do nothing;

-- ── Бренды ───────────────────────────────────────────────────────────────────
insert into brands (slug, name) values ('tambour', 'Tambour') on conflict (slug) do nothing;
insert into brands (slug, name) values ('nirlat', 'Nirlat')   on conflict (slug) do nothing;

-- ── Товары (8) ───────────────────────────────────────────────────────────────
-- Все привязаны к листовой категории interior-walls; бренд и характеристики варьируются.
insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type,
                      coverage_sqm_per_liter, drying_time_hours, is_active, is_featured, rating_avg, rating_count)
select c.id, b.id, p.sku, p.paint_type, p.usage_area, p.finish, p.base_type,
       p.coverage, p.drying, true, p.featured, p.rating, p.rcount
from (values
    ('TAM-INT-01', 'tambour', 'INTERIOR', 'INDOOR',  'MATTE',      'WATER',   12.0, 2.0, true,  4.7, 34),
    ('TAM-INT-02', 'tambour', 'INTERIOR', 'INDOOR',  'SATIN',      'WATER',   11.0, 3.0, false, 4.3, 12),
    ('TAM-EXT-01', 'tambour', 'EXTERIOR', 'OUTDOOR', 'MATTE',      'WATER',   10.0, 4.0, true,  4.5, 20),
    ('TAM-WOOD-01','tambour', 'WOOD',     'BOTH',    'GLOSS',      'SOLVENT', 14.0, 6.0, false, 4.1, 8),
    ('NIR-INT-01', 'nirlat',  'INTERIOR', 'INDOOR',  'MATTE',      'WATER',   12.5, 2.0, true,  4.8, 41),
    ('NIR-MET-01', 'nirlat',  'METAL',    'OUTDOOR', 'GLOSS',      'SOLVENT', 13.0, 5.0, false, 4.0, 6),
    ('NIR-PRIM-01','nirlat',  'PRIMER',   'BOTH',    'MATTE',      'WATER',    9.0, 1.5, false, 4.2, 9),
    ('NIR-ENAM-01','nirlat',  'ENAMEL',   'INDOOR',  'SEMI_GLOSS', 'SOLVENT', 12.0, 4.0, true,  4.4, 15)
) as p(sku, brand_slug, paint_type, usage_area, finish, base_type, coverage, drying, featured, rating, rcount)
join brands b on b.slug = p.brand_slug
cross join categories c
where c.slug = 'interior-walls'
on conflict (sku) do nothing;

-- Переводы товаров (he + en, name + короткое описание).
insert into product_translations (product_id, locale, name, short_description)
select pr.id, t.locale, t.name, t.short_desc
from products pr
join (values
    ('TAM-INT-01','he','צבע פנים מט','צבע קיר מט איכותי'), ('TAM-INT-01','en','Interior Matte','Premium matte wall paint'),
    ('TAM-INT-02','he','צבע פנים סאטן','גימור סאטן עדין'), ('TAM-INT-02','en','Interior Satin','Soft satin finish'),
    ('TAM-EXT-01','he','צבע חוץ','עמיד למזג אוויר'), ('TAM-EXT-01','en','Exterior Paint','Weather resistant'),
    ('TAM-WOOD-01','he','צבע לעץ','גימור מבריק לעץ'), ('TAM-WOOD-01','en','Wood Paint','Glossy wood finish'),
    ('NIR-INT-01','he','צבע פנים פרימיום','כיסוי מעולה'), ('NIR-INT-01','en','Premium Interior','Excellent coverage'),
    ('NIR-MET-01','he','צבע למתכת','הגנה מחלודה'), ('NIR-MET-01','en','Metal Paint','Anti-rust protection'),
    ('NIR-PRIM-01','he','פריימר','שכבת יסוד אוניברסלית'), ('NIR-PRIM-01','en','Primer','Universal base coat'),
    ('NIR-ENAM-01','he','אמייל','גימור חצי מבריק'), ('NIR-ENAM-01','en','Enamel','Semi-gloss finish')
) as t(sku, locale, name, short_desc) on t.sku = pr.sku
on conflict (product_id, locale) do nothing;

-- Варианты (цвет + объём + цена). Каждый товар получает 1–2 варианта.
insert into product_variants (product_id, sku, color_code, color_hex, volume_liters, price, old_price)
select pr.id, v.vsku, v.color_code, v.color_hex, v.volume, v.price, v.old_price
from products pr
join (values
    ('TAM-INT-01','TAM-INT-01-W-1','RAL9016','#F1F0EA',1.00,49.90,null),
    ('TAM-INT-01','TAM-INT-01-W-3','RAL9016','#F1F0EA',3.00,119.90,139.90),
    ('TAM-INT-02','TAM-INT-02-W-1','RAL9010','#FFFFFF',1.00,54.90,null),
    ('TAM-INT-02','TAM-INT-02-B-25','RAL5012','#3B83BD',2.50,124.90,null),
    ('TAM-EXT-01','TAM-EXT-01-W-3','RAL9016','#F1F0EA',3.00,139.90,null),
    ('TAM-WOOD-01','TAM-WOOD-01-BR-075','NCS-S-8010-Y30R','#5A4632',0.75,64.90,null),
    ('NIR-INT-01','NIR-INT-01-W-1','RAL9016','#F1F0EA',1.00,52.90,null),
    ('NIR-INT-01','NIR-INT-01-W-3','RAL9016','#F1F0EA',3.00,129.90,149.90),
    ('NIR-MET-01','NIR-MET-01-GY-1','RAL7016','#383E42',1.00,74.90,null),
    ('NIR-PRIM-01','NIR-PRIM-01-W-5','INT-WHITE','#FFFFFF',5.00,159.90,null),
    ('NIR-ENAM-01','NIR-ENAM-01-W-075','RAL9010','#FFFFFF',0.75,44.90,null),
    ('NIR-ENAM-01','NIR-ENAM-01-BK-25','RAL9005','#0A0A0A',2.50,119.90,null)
) as v(sku, vsku, color_code, color_hex, volume, price, old_price) on v.sku = pr.sku
on conflict (sku) do nothing;

-- ── Немного справочника цветов и один EAV-атрибут (для наглядности) ──────────
insert into color_swatches (code, system, hex, collection, is_popular) values
    ('RAL9016','RAL','#F1F0EA','RAL Classic', true),
    ('RAL9010','RAL','#FFFFFF','RAL Classic', true),
    ('RAL5012','RAL','#3B83BD','RAL Classic', false),
    ('RAL7016','RAL','#383E42','RAL Classic', true),
    ('RAL9005','RAL','#0A0A0A','RAL Classic', false)
on conflict (code) do nothing;

insert into product_attributes (code) values ('voc_gram_per_liter') on conflict (code) do nothing;

-- ── Услуги: портфолио (2 опубликованных проекта) ─────────────────────────────
insert into portfolio_projects (slug, property_type, city, area_sqm, duration_days, completed_at, is_published, sort_order)
values ('apartment-tel-aviv-2024', 'APARTMENT', 'Tel Aviv', 85.00, 7,  date '2024-11-15', true, 1),
       ('house-herzliya-2024',    'HOUSE',     'Herzliya', 180.00, 14, date '2024-12-20', true, 2)
on conflict (slug) do nothing;

insert into portfolio_project_work_types (project_id, work_type)
select p.id, w.work_type from portfolio_projects p
join (values ('apartment-tel-aviv-2024', 'WALLS'), ('apartment-tel-aviv-2024', 'CEILING'),
             ('house-herzliya-2024', 'WALLS'), ('house-herzliya-2024', 'FACADE'), ('house-herzliya-2024', 'DECORATIVE')
     ) as w(slug, work_type) on w.slug = p.slug
on conflict (project_id, work_type) do nothing;

insert into portfolio_translations (project_id, locale, title, description)
select p.id, t.locale, t.title, t.descr from portfolio_projects p
join (values
    ('apartment-tel-aviv-2024', 'he', 'שיפוץ דירה בתל אביב', 'צביעת דירת 3 חדרים'),
    ('apartment-tel-aviv-2024', 'en', 'Apartment in Tel Aviv', '3-room apartment repaint'),
    ('house-herzliya-2024', 'he', 'בית פרטי בהרצליה', 'צביעה פנים וחוץ'),
    ('house-herzliya-2024', 'en', 'Private house in Herzliya', 'Interior and facade painting')
) as t(slug, locale, title, descr) on t.slug = p.slug
on conflict (project_id, locale) do nothing;

insert into portfolio_media (project_id, url, kind, sort_order, pair_group)
select p.id, m.url, m.kind, m.sort_order, m.pair_group from portfolio_projects p
join (values
    ('apartment-tel-aviv-2024', 'https://cdn.tzevabest.test/pf/apt-livingroom-before.jpg', 'BEFORE', 1, 'livingroom'),
    ('apartment-tel-aviv-2024', 'https://cdn.tzevabest.test/pf/apt-livingroom-after.jpg',  'AFTER',  2, 'livingroom'),
    ('house-herzliya-2024',     'https://cdn.tzevabest.test/pf/house-facade-before.jpg',    'BEFORE', 1, 'facade'),
    ('house-herzliya-2024',     'https://cdn.tzevabest.test/pf/house-facade-after.jpg',     'AFTER',  2, 'facade')
) as m(slug, url, kind, sort_order, pair_group) on m.slug = p.slug
where not exists (select 1 from portfolio_media pm where pm.url = m.url);

-- ── Услуги: правила цен для калькулятора ─────────────────────────────────────
insert into service_pricing_rules (property_type, work_type, price_per_sqm_min, price_per_sqm_max, min_order_price)
values ('APARTMENT', 'WALLS',      25, 40, 800),
       ('APARTMENT', 'CEILING',    30, 45, 800),
       ('APARTMENT', 'WOOD',       40, 70, 1000),
       ('APARTMENT', 'DECORATIVE', 80, 150, 1500),
       ('HOUSE',     'WALLS',      22, 38, 1500),
       ('HOUSE',     'FACADE',     35, 60, 2500),
       ('OFFICE',    'WALLS',      20, 35, 2000),
       ('COMMERCIAL','WALLS',      18, 32, 3000)
on conflict (property_type, work_type) do nothing;

commit;
