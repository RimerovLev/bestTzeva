# Планы запросов и покрытие индексами — Tzeva Best

Замеры выполнены генератором `LoadDataGenerator` (профиль `local`, флаг
`app.dev.load-data=true`, масштаб `app.dev.load-data-scale=small|large`).

| Датасет | products | variants | requests | translations |
|---------|----------|----------|----------|--------------|
| **small** (по умолчанию) | 500   | 2 500  | 300    | ~1 000 |
| **large** (флаг, ~2 года) | 5 000 | 30 000 | 20 000 | ~10 000 |

> **Про размер и достоверность.** На `small` весь набор помещается в `shared_buffers`,
> и Seq Scan отрабатывает за доли мс — планировщику всё равно, поэтому сравнение «до/после»
> на 500 строках **ничего не доказывает**. Значимые выводы делаются по `large`; планы ниже
> помечены объёмом.
>
> **ANALYZE.** Генератор в конце выполняет явный `ANALYZE` — иначе EXPLAIN работал бы на
> дефолтных оценках и был бы ненадёжен с обеих сторон сравнения.

PostgreSQL 16, планы через `EXPLAIN (ANALYZE)`.

## Частые запросы и покрытие индексами

| # | Запрос | Индекс |
|---|--------|--------|
| 1 | Каталог: `category_id + is_active + paint_type`, sort `created_at desc` | `ix_products_catalog_list (category_id, paint_type, created_at desc) WHERE is_active` |
| 2 | Каталог: витрина «избранное» | `ix_products_featured (created_at desc) WHERE is_active and is_featured` |
| 3 | Каталог: поиск по имени `name ILIKE` | `ix_product_translations_name_trgm` (GIN pg_trgm) |
| 4 | Каталог: перевод по товару `(product_id, locale)` | `uq_product_translations_product_locale` |
| 5 | **Каталог: сортировка/фильтр по цене (min по вариантам)** | денормализованные `products.min_price/max_price` (V27) |
| 6 | Админ: заявки `status`, sort `created_at desc` | `ix_service_requests_status_created` |

## 1. Список каталога, 3 фильтра + пагинация — `paint_type` равенство

```sql
select p.id, pt.name, p.created_at
from products p
join product_translations pt on pt.product_id = p.id and pt.locale = 'en'
where p.is_active and p.category_id = 6 and p.paint_type = 'INTERIOR'
order by p.created_at desc
limit 20;
```

**LARGE** (категория ~417 товаров) — **0.45 мс**, узла Sort нет:

```
Nested Loop
  Index Scan using ix_products_catalog_list on products p          ← упорядоченный index scan
        Index Cond: (category_id = 6 AND paint_type = 'INTERIOR')   (is_active — частичный предикат)
  Index Scan using uq_product_translations_product_locale on pt
        Index Cond: (product_id = p.id AND locale = 'en')
Execution Time: 0.452 ms
```

**SMALL** — 0.19 мс, но план иной: Bitmap Index Scan + **Sort** (Bitmap не сохраняет порядок).
Это ровно тот случай, о котором предупреждал ревью: на 500 строках Sort «спрятан» и дёшев;
композитный индекс с `created_at desc` в хвосте окупается только когда планировщик доходит до
**Index Scan** — что и происходит на `large` (Sort исчезает).

## 2. Тот же список, но `paint_type IN (...)` (реальный фасетный фильтр, T-4.3)

```sql
... where p.is_active and p.category_id = 6 and p.paint_type in ('INTERIOR','EXTERIOR','WOOD')
order by p.created_at desc limit 20;
```

**LARGE** — **1.53 мс**, **Sort вернулся**, композитный индекс не выбран. Подтверждает замечание
ревью: при `IN` по `paint_type` (значение в середине ключа) сортировка по хвосту индекса **не
бесплатна**, и планировщик может вовсе не взять композитный индекс. Финальная форма индекса
зависит от настоящего запроса листинга с фасетами.

## 3. Витрина «избранное» — LARGE

`Index Scan using ix_products_featured` (частичный `WHERE is_active and is_featured`),
без Sort — доли мс.

## 5. Цена «от» / фильтр по цене — денормализация вместо агрегата (T-4.3, V27)

Раньше «цена от» считалась агрегатом `min(v.price)` по `product_variants` и на `large`
(30k+ вариантов) давала Seq Scan по большой таблице (~5.8 мс) — индексом на `products` не
закрывалась ни при каком порядке колонок. После V27 минимальная/максимальная цена и наличие
денормализованы в `products.min_price/max_price/has_stock` (ведёт триггер на `product_variants`),
поэтому фильтр/сортировка по цене и наличию идут по `products` без join к вариантам. Без корзины
(витрина — основной способ навигации) это стало главным путём фильтрации.

## 6. Поиск по имени (pg_trgm ILIKE) — LARGE (R-48, V36)

Раньше индекс `ix_product_translations_name_trgm` стоял на **сырой** колонке `name`, а условие
искало по `unaccent(regexp_replace(name, <никуд>))` — под выражение индекс по колонке не
подходил, поэтому шёл **гарантированный Seq Scan** на любом объёме.

V36 выносит нормализацию (снятие никуд + `immutable_unaccent`) в хранимую generated-колонку
`name_normalized` и строит по ней `ix_product_translations_name_norm_trgm` (`gin_trgm_ops`).
Поиск теперь `name_normalized ilike immutable_unaccent(<норм. запрос>)` — под колонку с
индексом. На 20 000 переводов с селективным токеном план:

```
Bitmap Heap Scan on product_translations
  Recheck Cond: (name_normalized ~~* '%zqxwvu%'::text)
  ->  Bitmap Index Scan on ix_product_translations_name_norm_trgm
        Index Cond: (name_normalized ~~* '%zqxwvu%'::text)
```

Проверяется тестом `ProductSearchIndexTest` (план обязан содержать индекс и не содержать
`Seq Scan on product_translations`). Старый индекс по `name` удалён в V36.

> **Фасеты (R-43 против R-48).** Каждый фасет считается со всеми фильтрами КРОМЕ своего —
> это 3 отдельных агрегата (тип/бренд/цена). Свести их в один `grouping sets`-запрос (идея
> R-48) без потери «исключи свой фильтр» нельзя: у фасетов разные наборы WHERE. Оставлено
> тремя запросами; выигрыш R-48 — на индексе поиска, не на числе агрегатов.

## Добавленные/исправленные индексы (V11)

```sql
create index ix_products_catalog_list on products (category_id, paint_type, created_at desc) where is_active;
create index ix_products_featured     on products (created_at desc) where is_active and is_featured;
```

## Удалён избыточный индекс (V25, R-31)

`ix_products_active_paint_type (is_active, paint_type)` перекрывался частичным
`ix_products_catalog_list` из V11 и вёл низкоселективным булевым полем. Удалён (V25). План листинга
каталога переснят на 20k товаров / 50 категорий (`ProductCatalogIndexTest`) — не ухудшился, идёт
через `ix_products_catalog_list`:

```text
Limit (actual time=0.013..0.023 rows=20 loops=1)
  ->  Index Scan using ix_products_catalog_list on products (actual time=0.012..0.021 rows=20 loops=1)
        Index Cond: ((category_id = 1) AND ((paint_type)::text = 'INTERIOR'::text))
Execution Time: 0.037 ms
```

`is_active` вынесен из ключа в частичный предикат (низкоселективен) — индекс меньше, а
`created_at desc` идёт сразу за equality-колонками.

## Вывод

- **Листинг каталога с равенством по `paint_type`** на объёме ~2 лет укладывается в единицы мс
  (0.45 мс) — покрыт индексом, Seq Scan по крупным таблицам нет.
- **Фильтр/сортировка по цене и наличию** после V27 (T-4.3) идёт по денормализованным полям
  `products.min_price/max_price/has_stock` без агрегата по вариантам.
- **Бюджет 50 мс на листинге каталога** окончательно проверяется на T-14.5 (нагрузка), когда
  появится реальный запрос листинга с фасетами; при `IN`-фильтре по `paint_type` форма
  `ix_products_catalog_list` может измениться.

> После сноса e-commerce (T-16.1) разделы про планы заказов и индексы `orders` удалены вместе со
> схемой; здесь остаются только каталог и заявки на услуги.
