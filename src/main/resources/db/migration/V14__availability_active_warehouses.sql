-- Доступное количество считается только по АКТИВНЫМ складам. Фильтр зашит в сами
-- представления, чтобы правило нельзя было обойти прямым обращением к вью.
create or replace view v_inventory_available as
select i.variant_id,
       i.warehouse_id,
       i.qty,
       i.reserved_qty,
       (i.qty - i.reserved_qty) as available_qty
from inventory i
join warehouses w on w.id = i.warehouse_id
where w.is_active;

create or replace view v_variant_available as
select i.variant_id,
       sum(i.qty)::int                as total_qty,
       sum(i.reserved_qty)::int       as total_reserved,
       sum(i.qty - i.reserved_qty)::int as available_qty
from inventory i
join warehouses w on w.id = i.warehouse_id
where w.is_active
group by i.variant_id;
