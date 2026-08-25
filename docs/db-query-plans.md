# Планы запросов и покрытие индексами — Tzeva Best

Замеры выполнены генератором `LoadDataGenerator` (профиль `local`, флаг
`app.dev.load-data=true`, масштаб `app.dev.load-data-scale=small|large`).

| Датасет | products | variants | orders | order_items | requests | translations |
|---------|----------|----------|--------|-------------|----------|--------------|
| **small** (по умолчанию) | 500   | 2 500  | 1 000   | 2 000   | 300    | ~1 000 |
| **large** (флаг, ~2 года) | 5 000 | 30 000 | 100 000 | 300 000 | 20 000 | ~10 000 |

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
| 5 | **Каталог: сортировка/фильтр по цене (min по вариантам)** | **не покрыт индексом на `products` — см. вывод** |
| 6 | Админ: заказы `status`, sort `created_at desc` | `ix_orders_status_created` |
| 7 | Админ: заявки `status`, sort `created_at desc` | `ix_service_requests_status_created` |

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

**LARGE** — **1.53 мс**, **Sort вернулся**, композитный индекс не выбран:

```
Sort  Sort Key: p.created_at DESC   (top-N heapsort)
  Hash Join
    Seq Scan on product_translations pt  (5000 en-строк)
    Bitmap Index Scan on ix_products_category_id   ← НЕ ix_products_catalog_list
          Index Cond: (category_id = 6)
Execution Time: 1.526 ms
```

Подтверждает замечание ревью: при `IN` по `paint_type` (значение в середине ключа) сортировка
по хвосту индекса **не бесплатна**, и планировщик может вовсе не взять композитный индекс.
Пока быстро (1.5 мс), но финальная форма индекса зависит от настоящего запроса листинга.

## 3. Витрина «избранное» — LARGE

`Index Scan using ix_products_featured` (частичный `WHERE is_active and is_featured`),
без Sort — доли мс.

## 4. Список заказов админки — LARGE (100 000 заказов, ~11 000 PAID)

```sql
select id, order_number, status, grand_total, created_at
from orders where status = 'PAID' order by created_at desc limit 50;
```

```
Index Scan using ix_orders_status_created on orders
      Index Cond: (status = 'PAID')
Execution Time: 0.281 ms
```

**Полностью покрыт** `ix_orders_status_created (status, created_at desc)` — Index Scan без Sort,
0.28 мс даже на 100k заказов. Seq Scan нет.

## 5. Самый дорогой запрос каталога — сортировка/фильтр по цене (агрегат по вариантам)

```sql
select p.id, min(v.price) fp
from products p join product_variants v on v.product_id = p.id
where p.is_active and p.category_id = 6
group by p.id order by fp limit 20;
```

**LARGE** — **5.8 мс**, и это узкое место:

```
Sort  Sort Key: min(v.price)
  HashAggregate
    Hash Join
      Seq Scan on product_variants v   (30 000 строк)   ← Seq Scan по большой таблице
      Bitmap Index Scan on ix_products_category_id
Execution Time: 5.795 ms
```

«Цена от» — это агрегат `min(price)` по `product_variants`, и **индексом на `products` он не
закрывается ни при каком порядке колонок**. На реальном объёме (250k+ вариантов) и вместе с
блоком фасетов (отдельный агрегирующий запрос по всей выборке из T-4.3) это и будет узким местом.

## 6. Поиск по имени (pg_trgm ILIKE) — LARGE (~10 000 переводов)

GIN-индекс `ix_product_translations_name_trgm` **создан** (проверено). На текущем объёме
планировщик выбирает Seq Scan (~3 мс). Принудительно (`enable_seqscan=off`) индекс берётся и
даёт **Bitmap Index Scan за 0.35 мс** — то есть индекс рабочий, просто на этом объёме дешевле
Seq Scan; на проде (сотни тысяч переводов) планировщик переключится сам. Отдельное внимание —
на T-4.5.

## Добавленные/исправленные индексы (V11)

```sql
create index ix_products_catalog_list on products (category_id, paint_type, created_at desc) where is_active;
create index ix_products_featured     on products (created_at desc) where is_active and is_featured;
```

`is_active` вынесен из ключа в частичный предикат (низкоселективен) — индекс меньше, а
`created_at desc` идёт сразу за equality-колонками.

## Вывод

- **Список заказов админки** и **листинг каталога с равенством по `paint_type`** на объёме
  ~2 лет укладываются в единицы мс (0.28 / 0.45 мс) — покрыты индексами, Seq Scan по крупным
  таблицам нет.
- **Бюджет 50 мс на листинге каталога пока НЕ подтверждён.** Решающие запросы —
  сортировка/фильтр по цене (агрегат по `product_variants`) и блок фасетов — в коде ещё
  отсутствуют (T-4.3). Их и надо мерить; проверка бюджета **переносится на T-4.3 и
  окончательно на T-14.5**.
- Индекс `ix_products_catalog_list` доведён до частичной формы, но при `IN`-фильтре по
  `paint_type` его форма может измениться — уточняется на T-4.3, когда появится реальный
  запрос листинга с фасетами.
