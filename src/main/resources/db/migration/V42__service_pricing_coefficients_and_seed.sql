-- ─────────────────────────────────────────────────────────────────────────────
-- T-11.2: калькулятор стоимости покраски. Коэффициенты высоты потолков (>3м) и
-- срочности, а также опций (вынос мебели, подготовка, грунтовка, работа на
-- высоте) в схеме — их там не было. Плюс seed тарифов (в V6 их нет).
-- ─────────────────────────────────────────────────────────────────────────────

create table service_pricing_coefficients (
    id         bigint generated always as identity primary key,
    code       varchar(32)  not null unique,
    multiplier numeric(5,3) not null,
    is_active  boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint ck_service_pricing_coefficients_multiplier check (multiplier > 0)
);

create trigger trg_service_pricing_coefficients_set_updated_at
    before update on service_pricing_coefficients for each row execute function set_updated_at();

insert into service_pricing_coefficients (code, multiplier) values
    ('CEILING_HEIGHT_OVER_3M', 1.200),
    ('URGENCY',               1.300),
    ('OPT_FURNITURE_REMOVAL', 1.050),
    ('OPT_SURFACE_PREP',      1.150),
    ('OPT_PRIMING',           1.100),
    ('OPT_HEIGHT_WORK',       1.250);

-- Seed тарифов: тип объекта × вид работ. Базовые ставки ₪/м² с наценкой по типу объекта.
insert into service_pricing_rules (property_type, work_type, price_per_sqm_min, price_per_sqm_max, min_order_price)
select p.pt, w.wt,
       round((w.bmin * p.factor)::numeric, 2),
       round((w.bmax * p.factor)::numeric, 2),
       500
from (values ('APARTMENT', 1.0), ('HOUSE', 1.1), ('OFFICE', 1.2), ('COMMERCIAL', 1.3), ('OTHER', 1.0))
         as p(pt, factor),
     (values ('WALLS', 25, 40), ('CEILING', 30, 45), ('FACADE', 35, 55),
             ('WOOD', 50, 80), ('METAL', 55, 85), ('DECORATIVE', 70, 120))
         as w(wt, bmin, bmax)
on conflict (property_type, work_type) do nothing;
