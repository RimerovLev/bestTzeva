-- ─────────────────────────────────────────────────────────────────────────────
-- R-14 + R-15: ограничение глубины дерева категорий (максимум 3 уровня).
--
-- R-14: прежняя версия проверяла только глубину самой изменяемой строки, но не
--       её потомков. Перенос поддерева под более глубокую категорию проваливал
--       потомков за 3-й уровень незамеченным. Теперь учитывается и высота
--       поддерева переносимой категории: глубина самого глубокого потомка после
--       переноса не должна превышать 3.
-- R-15: raise без кода давал SQLSTATE P0001 → UncategorizedSQLException → 500.
--       Теперь errcode 23514 (check_violation, класс 23) → DataIntegrityViolation
--       → 409 problem+json.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function enforce_category_max_depth() returns trigger as $$
declare
    new_depth      int := 1;      -- глубина переносимой строки на новом месте
    parent         bigint := new.parent_id;
    subtree_height int;           -- число уровней в поддереве строки, включая её саму
    guard          int := 0;      -- страховка от цикла в parent_id
begin
    -- Глубина новой позиции: поднимаемся к корню от нового родителя.
    while parent is not null loop
        new_depth := new_depth + 1;
        guard := guard + 1;
        if guard > 100 then
            raise exception 'category parent chain too long or cyclic (slug=%)', new.slug
                using errcode = '23514';
        end if;
        select parent_id into parent from categories where id = parent;
    end loop;

    -- Высота поддерева переносимой категории: спускаемся к потомкам.
    -- При INSERT потомков ещё нет → высота 1. При UPDATE parent_id потомки строки
    -- по-прежнему ссылаются на new.id, поэтому CTE их находит.
    with recursive subtree as (
        select new.id as id, 1 as lvl
        union all
        select c.id, s.lvl + 1
        from categories c
        join subtree s on c.parent_id = s.id
    )
    select max(lvl) into subtree_height from subtree;

    if subtree_height is null then
        subtree_height := 1;
    end if;

    if new_depth + subtree_height - 1 > 3 then
        raise exception 'category tree depth exceeds 3 levels (slug=%)', new.slug
            using errcode = '23514';
    end if;

    return new;
end;
$$ language plpgsql;
