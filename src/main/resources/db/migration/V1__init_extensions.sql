-- Расширения PostgreSQL, используемые схемой Tzeva Best.
-- pg_trgm  — триграммный поиск (LIKE/ILIKE, похожесть) для будущего поиска по каталогу.
-- unaccent — снятие диакритики для поиска без учёта акцентов.
create extension if not exists pg_trgm;
create extension if not exists unaccent;
